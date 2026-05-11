// AI搜索功能
function initSearchChat() {
    const chatMessages = document.getElementById('searchMessages');
    const input = document.getElementById('searchInput');
    const sendBtn = document.getElementById('searchSend');
    const settingsToggle = document.getElementById('searchSettingsToggle');
    const settingsPanel = document.getElementById('searchSettingsPanel');

    if (!chatMessages || !input || !sendBtn) {
        return;
    }

    let isLoading = false;

    const settings = {
        topK: 10,
        enableRerank: true,
        enableEvaluation: true,
        userId: '',
        sessionId: ''
    };

    const history = store.getMessages('search');
    if (history.length > 0) {
        history.forEach(msg => {
            if (msg.isUser) {
                addUserQuery(msg.content);
            } else {
                addSearchResult(msg.content);
            }
        });
    } else {
        showWelcome();
    }

    sendBtn.addEventListener('click', () => executeSearch());
    input.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') executeSearch();
    });

    settingsToggle.addEventListener('click', () => {
        settingsPanel.classList.toggle('open');
    });

    document.getElementById('searchTopK').addEventListener('change', (e) => {
        settings.topK = parseInt(e.target.value) || 10;
    });
    document.getElementById('searchRerank').addEventListener('change', (e) => {
        settings.enableRerank = e.target.checked;
    });
    document.getElementById('searchEvaluation').addEventListener('change', (e) => {
        settings.enableEvaluation = e.target.checked;
    });
    document.getElementById('searchUserId').addEventListener('input', (e) => {
        settings.userId = e.target.value.trim();
    });
    document.getElementById('searchSessionId').addEventListener('input', (e) => {
        settings.sessionId = e.target.value.trim();
    });

    async function executeSearch() {
        const query = input.value.trim();
        if (!query || isLoading) return;

        addUserQuery(query);
        input.value = '';
        showTypingIndicator();

        isLoading = true;
        sendBtn.disabled = true;

        try {
            const params = {
                query: query,
                topK: settings.topK,
                enableRerank: settings.enableRerank,
                enableEvaluation: settings.enableEvaluation
            };
            if (settings.userId) params.userId = settings.userId;
            if (settings.sessionId) params.sessionId = settings.sessionId;

            const result = await api.search.ask(params);
            removeTypingIndicator();
            addSearchResult(result);

            store.addMessage('search', { content: query, isUser: true });
            store.addMessage('search', { content: result, isUser: false });
            store.addSearchHistory(query);
        } catch (error) {
            removeTypingIndicator();
            addErrorMessage('搜索失败，请稍后重试。');
        }

        isLoading = false;
        sendBtn.disabled = false;
    }

    function addUserQuery(query) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message user';
        messageDiv.innerHTML = `
            <div class="message-avatar">😊</div>
            <div class="message-content">${escapeHtml(query)}</div>
        `;
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function addSearchResult(result) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message bot';
        messageDiv.innerHTML = `
            <div class="message-avatar">🔎</div>
            <div class="message-content search-result-content">
                ${buildAnswerSection(result)}
                ${buildDocumentsSection(result)}
                ${buildEvaluationSection(result)}
                ${buildStatsSection(result)}
            </div>
        `;
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function buildAnswerSection(result) {
        if (!result.answer) return '';
        return `
            <div class="search-answer">
                <div class="search-section-title">💡 智能回答</div>
                <div class="search-answer-text">${renderMarkdown(result.answer)}</div>
            </div>
        `;
    }

    function buildDocumentsSection(result) {
        const count = result.goodsCount || 0;
        if (count === 0) return '';

        return `
            <div class="search-documents">
                <div class="search-section-title">📦 相关商品 <span class="search-count-badge">${count}件</span></div>
                <div class="search-doc-list">
                    ${buildDocumentItems(result)}
                </div>
            </div>
        `;
    }

    function buildDocumentItems(result) {
        if (!result.documents || result.documents.length === 0) {
            return '<div class="search-doc-empty">暂无商品详情</div>';
        }

        return result.documents.map((doc, index) => `
            <div class="search-doc-item ${doc.lowQuality ? 'low-quality' : ''}">
                <div class="search-doc-header">
                    <span class="search-doc-index">${index + 1}</span>
                    <span class="search-doc-title">${escapeHtml(doc.title || '未知商品')}</span>
                    ${doc.finalScore > 0 ? `<span class="search-doc-score">相关度 ${(doc.finalScore * 100).toFixed(1)}%</span>` : ''}
                </div>
                ${doc.content ? `<div class="search-doc-content">${escapeHtml(truncateText(doc.content, 120))}</div>` : ''}
                ${doc.keywords && doc.keywords.length > 0 ? `
                    <div class="search-doc-keywords">
                        ${doc.keywords.map(k => `<span class="search-keyword-tag">${escapeHtml(k)}</span>`).join('')}
                    </div>
                ` : ''}
                ${doc.lowQuality ? '<div class="search-doc-warning">⚠️ 低质量结果</div>' : ''}
            </div>
        `).join('');
    }

    function buildEvaluationSection(result) {
        if (!result.qualityLevel || result.qualityLevel === 'cached') return '';

        const qualityColor = getQualityColor(result.qualityLevel);
        const f1Percent = ((result.f1Score || 0) * 100).toFixed(1);

        return `
            <div class="search-evaluation">
                <div class="search-section-title">📊 质量评估</div>
                <div class="search-eval-grid">
                    <div class="search-eval-item">
                        <div class="search-eval-label">质量等级</div>
                        <div class="search-eval-value" style="color: ${qualityColor}">${result.qualityLevel}</div>
                    </div>
                    <div class="search-eval-item">
                        <div class="search-eval-label">F1分数</div>
                        <div class="search-eval-value">${f1Percent}%</div>
                    </div>
                </div>
                <div class="search-quality-bar">
                    <div class="search-quality-fill" style="width: ${f1Percent}%; background: ${qualityColor}"></div>
                </div>
            </div>
        `;
    }

    function buildStatsSection(result) {
        const totalTime = result.totalTime || 0;
        const cacheHit = result.cacheHit || false;
        const personalized = result.personalized || false;

        return `
            <div class="search-stats">
                <div class="search-section-title">⚡ 搜索统计</div>
                <div class="search-stats-grid">
                    <div class="search-stat-item">
                        <span class="search-stat-value">${totalTime}</span>
                        <span class="search-stat-label">总耗时(ms)</span>
                    </div>
                    <div class="search-stat-item">
                        <span class="search-stat-value">${cacheHit ? '✅' : '❌'}</span>
                        <span class="search-stat-label">缓存命中</span>
                    </div>
                    <div class="search-stat-item">
                        <span class="search-stat-value">${personalized ? '✅' : '❌'}</span>
                        <span class="search-stat-label">个性化</span>
                    </div>
                </div>
            </div>
        `;
    }

    function addErrorMessage(message) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message bot';
        messageDiv.innerHTML = `
            <div class="message-avatar">🔎</div>
            <div class="message-content">
                <div class="search-error">${escapeHtml(message)}</div>
            </div>
        `;
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function showTypingIndicator() {
        const indicator = document.createElement('div');
        indicator.className = 'message bot';
        indicator.id = 'searchTypingIndicator';
        indicator.innerHTML = `
            <div class="message-avatar">🔎</div>
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
        const indicator = document.getElementById('searchTypingIndicator');
        if (indicator) indicator.remove();
    }

    function showWelcome() {
        chatMessages.innerHTML = `
            <div class="welcome">
                <div class="welcome-icon">🔎</div>
                <h2>欢迎使用AI搜索</h2>
                <p>我可以帮您智能搜索商品，支持语义理解、向量召回、智能重排和质量评估。</p>
                <div class="suggestions">
                    <span class="suggestion-chip">智能台灯有哪些功能</span>
                    <span class="suggestion-chip">保温效果好的杯子</span>
                    <span class="suggestion-chip">适合送礼的产品</span>
                </div>
            </div>
        `;

        document.querySelectorAll('.suggestion-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                input.value = chip.textContent;
                executeSearch();
            });
        });
    }

    function getQualityColor(level) {
        const colors = {
            '优秀': '#28a745',
            '良好': '#17a2b8',
            '一般': '#ffc107',
            '较差': '#dc3545'
        };
        return colors[level] || '#6c757d';
    }

    function truncateText(text, maxLength) {
        if (!text) return '';
        return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
    }

    function renderMarkdown(text) {
        let html = escapeHtml(text);
        html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
        html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
        html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
        html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
        html = html.replace(/^(\d+)\. (.+)$/gm, '<li>$1. $2</li>');
        html = html.replace(/(<li>.+?<\/li>)+/gs, '<ul>$&</ul>');
        html = html.replace(/\n\n/g, '</p><p>');
        html = html.replace(/\n/g, ' ');
        if (!html.startsWith('<')) {
            html = `<p>${html}</p>`;
        }
        return html;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}
