let stompClient = null;
let activeFile = ""; 
let editorInstance = null;
let isApplyingNetworkUpdate = false;
let pieChartInstance = null;
let liveStatsInterval = null;

window.monacoReady.then(() => {
    
    // 1. Initialize Editor
    editorInstance = monaco.editor.create(document.getElementById('editor-container'), {
        value: '// Booting Workspace...', language: 'java', theme: 'vs-dark', automaticLayout: true
    });

    let debounceTimer;
    editorInstance.onDidChangeModelContent(() => {
        if (isApplyingNetworkUpdate || !activeFile) return;
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            if (stompClient && stompClient.connected) {
                // FULL PAYLOAD SYNC
                stompClient.send("/app/edit", {}, JSON.stringify({
                    fileName: activeFile, 
                    content: editorInstance.getValue(),
                    user: currentUser, 
                    role: currentRole 
                }));
            }
        }, 300); // Back to 300ms debounce for full payloads
    });

    // 2. Initialize WebSocket Connection
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        
        stompClient.subscribe('/topic/updates', function (msg) {
            const body = JSON.parse(msg.body);
            if (body.type === "ERROR" && body.user === currentUser) {
                alert("SERVER: " + body.content);
                return;
            }
            if (body.fileName !== activeFile || body.user === currentUser) return;

            if (body.type === "UPDATE" && editorInstance) {
                isApplyingNetworkUpdate = true;
                editorInstance.setValue(body.content);
                isApplyingNetworkUpdate = false;
            }
        });

        stompClient.subscribe('/topic/files', () => fetchFiles());
        stompClient.subscribe('/topic/public', function (msg) { displayChatMessage(JSON.parse(msg.body)); });
        
        stompClient.subscribe('/topic/presence', function (msg) {
            if (currentRole === 'ADMIN') {
                const presence = JSON.parse(msg.body);
                let html = "";
                for (const [user, file] of Object.entries(presence)) {
                    html += `<div style="margin-bottom:5px;"><b>${user}</b> ➡️ <span style="color:#007acc;">${file}</span></div>`;
                }
                const monitor = document.getElementById('adminMonitor');
                if(monitor) monitor.innerHTML = html || "No active users.";
            }
        });

        fetchFiles();
    });

    // 3. UI Events
    document.getElementById('btnChatToggle').onclick = () => {
        document.getElementById('chatPanel').classList.toggle('open');
        document.getElementById('work-area').classList.toggle('chat-open');
    };
    document.getElementById('btnClearTerm').onclick = () => { document.getElementById('terminalOutput').innerText = "System Ready..."; };

    function sendChat() {
        const input = document.getElementById('chatInput');
        if (input.value.trim() && stompClient && stompClient.connected) {
            const prefix = currentRole === 'ADMIN' ? '[ADMIN] ' : '';
            stompClient.send("/app/chat.send", {}, JSON.stringify({ sender: currentUser, content: prefix + input.value.trim() }));
            input.value = '';
        }
    }
    document.getElementById('btnSendChat').onclick = sendChat;
    document.getElementById('chatInput').onkeydown = (e) => { if (e.key === 'Enter') sendChat(); };

    document.getElementById('btnSave').onclick = () => {
        if (!activeFile) return;
        const link = document.createElement("a");
        link.href = URL.createObjectURL(new Blob([editorInstance.getValue()], { type: "text/plain" }));
        link.download = activeFile.split('/').pop();
        link.click();
    };

    document.getElementById('btnUpload').onclick = () => document.getElementById('fileUpload').click();
    document.getElementById('fileUpload').onchange = (e) => {
        const file = e.target.files[0];
        if (!file) return;
        if (!['.java', '.py', '.txt'].some(ext => file.name.toLowerCase().endsWith(ext))) {
            e.target.value = ''; 
            return alert("UPLOAD BLOCKED: Only .java, .py, and .txt files are allowed.");
        }
        const reader = new FileReader();
        reader.onload = (ev) => {
            const folder = currentRole === 'ADMIN' ? 'admin' : currentUser;
            stompClient.send("/app/files.create", {}, JSON.stringify({ name: file.name, creator: folder, role: currentRole }));
            setTimeout(() => {
                activeFile = `${folder}/${file.name}`;
                editorInstance.setValue(ev.target.result);
                pingPresence();
                fetchFiles();
            }, 500);
        };
        reader.readAsText(file);
        e.target.value = '';
    };

    document.getElementById('createFileBtn').onclick = () => {
        const name = document.getElementById('newFileInput').value;
        const folder = currentRole === 'ADMIN' ? 'admin' : currentUser; 
        if (name && stompClient) {
            stompClient.send("/app/files.create", {}, JSON.stringify({ name: name, creator: folder, role: currentRole }));
            document.getElementById('newFileInput').value = '';
        }
    };

    // DIRECT BROWSER COMPILER CALL
    // DIRECT BROWSER COMPILER CALL (With Strict Versioning)
    // ROUTED BACKEND COMPILER CALL
    document.getElementById('btnRun').onclick = async () => {
        if (!activeFile) return alert("Select a file to run.");
        const terminal = document.getElementById('terminalOutput');
        terminal.innerText = "Routing to Secure Backend Compiler...\n";
        
        const isPython = activeFile.endsWith(".py");
        const payload = {
            language: isPython ? "python" : "java",
            version: isPython ? "3.10.0" : "15.0.2", 
            files: [{ content: editorInstance.getValue() }]
        };

        try {
            // FIX: We are now sending the code to YOUR Spring Boot server (/api/run)
            const response = await fetch("/api/run", {
                method: "POST", 
                headers: { "Content-Type": "application/json" }, 
                body: JSON.stringify(payload)
            });
            
            const textResponse = await response.text();
            
            try {
                const data = JSON.parse(textResponse);
                
                if (data.compile && data.compile.code !== 0) {
                    terminal.innerText = "COMPILE ERROR:\n" + data.compile.output;
                } else if (data.run && data.run.output) {
                    terminal.innerText = data.run.output;
                } else {
                    terminal.innerText = "Execution Complete (No Output)";
                }
            } catch (parseErr) {
                terminal.innerText = "Proxy Error:\n" + textResponse;
            }
            
        } catch (err) { 
            terminal.innerText = "SERVER DISCONNECTED: " + err.message + "\nEnsure your Spring Boot backend is running."; 
        }
    };

    document.getElementById('btnStats').onclick = () => {
        document.getElementById('statsModal').style.display = 'block';
        updateStats(); 
        liveStatsInterval = setInterval(updateStats, 2000); 
    };

    document.getElementById('btnCloseStats').onclick = () => {
        document.getElementById('statsModal').style.display = 'none';
        clearInterval(liveStatsInterval);
    };
});

// --- HELPER FUNCTIONS ---
function fetchFiles() {
    fetch('/api/files').then(res => res.json()).then(data => {
        renderFileList(data);
        if (!data.includes(activeFile) && data.length > 0) {
            activeFile = data[0];
            loadFileContent(activeFile);
            pingPresence();
        } else if (data.length === 0 && editorInstance) {
            activeFile = "";
            isApplyingNetworkUpdate = true;
            editorInstance.setValue("// Workspace empty. Click + CREATE to begin.");
            isApplyingNetworkUpdate = false;
        }
    });
}

function renderFileList(files) {
    const list = document.getElementById('file-list');
    list.innerHTML = '';
    files.forEach(path => {
        const div = document.createElement('div');
        div.className = `file-row ${path === activeFile ? 'active' : ''}`;
        const folder = path.split('/')[0];
        const name = path.split('/')[1] || path;
        
        div.innerHTML = `<span style="color:#666; font-size:10px;">[${folder}]</span> ${name}`;
        div.onclick = () => {
            activeFile = path;
            loadFileContent(path);
            pingPresence();
            renderFileList(files);
        };

        if (currentRole === 'ADMIN') {
            const delBtn = document.createElement('button');
            delBtn.innerHTML = '×';
            delBtn.style.cssText = 'margin-left:auto; background:none; border:none; color:red; cursor:pointer; font-weight:bold;';
            delBtn.onclick = (e) => {
                e.stopPropagation();
                if(confirm(`Delete ${path}?`)) stompClient.send("/app/files.delete", {}, JSON.stringify({ path: path, role: currentRole }));
            };
            div.appendChild(delBtn);
        }
        list.appendChild(div);
    });
}

function loadFileContent(path) {
    if (!path) return;
    fetch(`/api/editor/content?path=${encodeURIComponent(path)}`)
        .then(res => res.text())
        .then(text => {
            if (editorInstance) {
                isApplyingNetworkUpdate = true;
                editorInstance.setValue(text);
                monaco.editor.setModelLanguage(editorInstance.getModel(), path.endsWith('.py') ? 'python' : 'java');
                isApplyingNetworkUpdate = false;
            }
        });
}

function displayChatMessage(msg) {
    const box = document.getElementById('chatMessages');
    const div = document.createElement('div');
    div.className = 'chat-msg';
    const color = msg.content.startsWith('[ADMIN]') ? '#ff4d4d' : (msg.sender === currentUser ? '#007acc' : '#e0ab18');
    div.innerHTML = `<b style="color: ${color}">${msg.sender}:</b> ${msg.content} <span style="font-size:9px; color:#555; float:right;">${msg.timestamp}</span>`;
    box.appendChild(div);
    box.scrollTop = box.scrollHeight;
}

function pingPresence() {
    if (stompClient && stompClient.connected && activeFile) {
        stompClient.send("/app/presence", {}, JSON.stringify({ user: currentUser, file: activeFile }));
    }
}

window.handleLogout = function() {
    if (confirm('Securely logout?')) {
        if (stompClient && stompClient.connected) {
            stompClient.disconnect(() => { window.location.href = '/logout'; });
        } else { window.location.href = '/logout'; }
    }
};

async function updateStats() {
    try {
        const res = await fetch("/api/stats");
        if (!res.ok) return;
        const data = await res.json(); // Now directly returns the edit map
        
        const users = Object.keys(data);
        const edits = Object.values(data);
        
        const ctx = document.getElementById('contributionChart').getContext('2d');
        if (!pieChartInstance) {
            pieChartInstance = new Chart(ctx, {
                type: 'pie',
                data: { labels: users, datasets: [{ data: edits, backgroundColor: ['#007acc', '#e0ab18', '#2ea043', '#d32f2f', '#8a2be2'], borderColor: '#1e1e1e' }] },
                options: { animation: false, plugins: { legend: { labels: { color: 'white' } } } }
            });
        } else {
            pieChartInstance.data.labels = users;
            pieChartInstance.data.datasets[0].data = edits;
            pieChartInstance.update();
        }
    } catch(e) { console.error("Stats API failed"); }
}