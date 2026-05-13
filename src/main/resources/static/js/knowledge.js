// 知识检索聊天功能
function initKnowledgeChat() {
    const chatMessages = document.getElementById('knowledgeMessages');
    const input = document.getElementById('knowledgeInput');
    const sendBtn = document.getElementById('knowledgeSend');
    
    let isLoading = false;
    
    // 加载历史消息
    const history = store.getMessages('knowledge');
    if (history.length > 0) {
        history.forEach(msg => {
            addMessage(msg.content, msg.isUser, msg.source);
        });
    } else {
        // 显示欢迎界面
        showWelcome();
    }
    
    // 发送按钮点击事件
    sendBtn.addEventListener('click', () => sendMessage());
    
    // 回车键发送
    input.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });
    
    async function sendMessage() {
        const message = input.value.trim();
        if (!message || isLoading) return;
        
        // 添加用户消息
        addMessage(message, true);
        input.value = '';
        
        // 显示打字指示器
        showTypingIndicator();
        
        isLoading = true;
        sendBtn.disabled = true;
        
        try {
            // 调用API
            const response = await api.knowledge.query({ query: message });
            
            // 移除打字指示器
            removeTypingIndicator();
            
            // 添加回复消息（包含来源信息）
            addMessage(response.answer || '抱歉，暂时无法回答您的问题。', false, response.source);
            
            // 保存到历史记录
            store.addMessage('knowledge', { content: message, isUser: true });
            store.addMessage('knowledge', { content: response.answer, source: response.source, isUser: false });
            
        } catch (error) {
            removeTypingIndicator();
            addMessage('网络连接失败，请稍后重试。', false);
        }
        
        isLoading = false;
        sendBtn.disabled = false;
    }
    
    function addMessage(content, isUser, source) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isUser ? 'user' : 'bot'}`;
        
        const sourceHtml = source ? `<div class="message-source">📄 来源: ${escapeHtml(source)}</div>` : '';
        
        messageDiv.innerHTML = `
            <div class="message-avatar">${isUser ? '😊' : '🤖'}</div>
            <div class="message-body">
                <div class="message-content">${renderMarkdown(content)}</div>
                ${sourceHtml}
            </div>
        `;
        
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
    
    // 简单的Markdown渲染器
    function renderMarkdown(text) {
        // 先转义HTML特殊字符
        let html = escapeHtml(text);
        
        // 处理标题
        html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
        html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
        html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
        
        // 处理粗体和斜体
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
        
        // 处理表格（先处理，避免被列表规则影响）
        // 处理多行标准Markdown表格
        html = html.replace(/\|(.+)\|\n\|[-|]+\|\n((?:\|.+\|\n?)+)/g, function(match, header, body) {
            const headerCells = header.split('|').filter(c => c.trim()).map(c => `<th>${c.trim()}</th>`).join('');
            const bodyRows = body.trim().split('\n').map(row => {
                const cells = row.split('|').filter(c => c.trim()).map(c => `<td>${c.trim()}</td>`).join('');
                return `<tr>${cells}</tr>`;
            }).join('');
            return `<table><thead><tr>${headerCells}</tr></thead><tbody>${bodyRows}</tbody></table>`;
        });
        
        // 处理单行表格格式（用户输入的特殊格式）
        html = parseSingleLineTable(html);
        
        // 处理分隔线
        html = html.replace(/^---$/gm, '<hr>');
        
        // 处理无序列表
        html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
        
        // 处理有序列表
        html = html.replace(/^(\d+)\. (.+)$/gm, '<li>$1. $2</li>');
        
        // 处理列表包裹
        html = html.replace(/(<li>.+?<\/li>)+/gs, '<ul>$&</ul>');
        
        // 处理段落间的空行（连续两个换行）
        html = html.replace(/\n\n/g, '</p><p>');
        
        // 处理单个换行（保留但不产生额外间距）
        html = html.replace(/\n/g, ' ');
        
        // 添加段落标签包裹
        if (!html.startsWith('<')) {
            html = `<p>${html}</p>`;
        }
        
        return html;
    }
    
    function showTypingIndicator() {
        const indicator = document.createElement('div');
        indicator.className = 'message bot';
        indicator.id = 'typingIndicator';
        indicator.innerHTML = `
            <div class="message-avatar">🤖</div>
            <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
            </div>
        `;
        chatMessages.appendChild(indicator);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
    
    function removeTypingIndicator() {
        const indicator = document.getElementById('typingIndicator');
        if (indicator) indicator.remove();
    }
    
    function showWelcome() {
        chatMessages.innerHTML = `
            <div class="welcome">
                <div class="welcome-icon">💡</div>
                <h2>欢迎使用知识检索助手</h2>
                <p>我可以帮您解答关于产品说明书的各类问题，例如功能介绍、使用方法、注意事项等。</p>
                <div class="suggestions">
                    <span class="suggestion-chip">智能台灯有哪些功能？</span>
                    <span class="suggestion-chip">咖啡杯如何保温？</span>
                    <span class="suggestion-chip">产品售后政策</span>
                </div>
            </div>
        `;
        
        // 添加快捷问题点击事件
        document.querySelectorAll('.suggestion-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                input.value = chip.textContent;
                sendMessage();
            });
        });
    }
    
    // 解析单行表格格式
    function parseSingleLineTable(text) {
        // 匹配单行表格模式：| 列1 | 列2 | :--- | :--- | | 值1 | 值2 | ...
        const tableRegex = /\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*:---\s*\|\s*:---\s*\|((?:\s*\|\s*[^|]+?\s*\|\s*[^|]+?\s*\|)+)/g;
        
        return text.replace(tableRegex, function(match, col1, col2, body) {
            // 构建表头
            const headerCells = `<th>${col1.trim()}</th><th>${col2.trim()}</th>`;
            
            // 解析表体数据
            const bodyCells = body.split('|').filter(c => c.trim());
            const bodyRows = [];
            
            for (let i = 0; i < bodyCells.length; i += 2) {
                const cell1 = bodyCells[i] || '';
                const cell2 = bodyCells[i + 1] || '';
                bodyRows.push(`<tr><td>${cell1.trim()}</td><td>${cell2.trim()}</td></tr>`);
            }
            
            return `<table><thead><tr>${headerCells}</tr></thead><tbody>${bodyRows.join('')}</tbody></table>`;
        });
    }
    
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}
