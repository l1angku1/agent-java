// 路由管理
const router = {
    routes: {
        'knowledge': { title: '知识检索', icon: '🔍' },
        'search': { title: 'AI搜索', icon: '🔎' },
        'plan': { title: '计划管理', icon: '📋' },
        'memory': { title: '记忆管理', icon: '🧠' }
    },
    
    currentRoute: 'knowledge',
    
    navigate(route) {
        if (this.routes[route]) {
            this.currentRoute = route;
            store.setCurrentPage(route);
            this.updateUI();
            window.history.pushState({ route }, '', `/${route}`);
        }
    },
    
    updateUI() {
        // 更新页面标题
        document.title = `${this.routes[this.currentRoute].title} - 智能助手`;
        
        // 更新导航高亮
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
        });
        const activeItem = document.querySelector(`[data-route="${this.currentRoute}"]`);
        if (activeItem) {
            activeItem.classList.add('active');
        }
        
        // 加载对应页面内容
        this.loadPage(this.currentRoute);
    },
    
    async loadPage(route) {
        const contentArea = document.getElementById('pageContent');
        if (!contentArea) return;
        
        contentArea.innerHTML = '<div class="loading"><span class="loading-spinner"></span> 加载中...</div>';
        
        try {
            // 根据路由加载不同的页面组件
            const pageModules = {
                'knowledge': loadKnowledgePage,
                'search': loadSearchPage,
                'plan': loadPlanPage,
                'memory': loadMemoryPage
            };
            
            if (pageModules[route]) {
                await pageModules[route](contentArea);
            } else {
                contentArea.innerHTML = '<div class="empty-state"><div class="empty-icon">📄</div><div class="empty-title">页面未找到</div><div class="empty-desc">该页面不存在</div></div>';
            }
        } catch (error) {
            console.error('Failed to load page:', error);
            contentArea.innerHTML = '<div class="empty-state"><div class="empty-icon">❌</div><div class="empty-title">页面加载失败</div><div class="empty-desc">请刷新页面重试</div></div>';
        }
    },
    
    init() {
        // 解析URL路径
        const path = window.location.pathname.slice(1) || 'knowledge';
        if (this.routes[path]) {
            this.currentRoute = path;
        }
        
        // 设置导航点击事件
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', () => {
                const route = item.getAttribute('data-route');
                if (route) {
                    this.navigate(route);
                    // 移动端点击后关闭侧边栏
                    const sidebar = document.getElementById('sidebar');
                    if (sidebar) {
                        sidebar.classList.remove('open');
                    }
                }
            });
        });
        
        // 设置菜单按钮点击事件（移动端）
        const menuBtn = document.getElementById('menuBtn');
        if (menuBtn) {
            menuBtn.addEventListener('click', () => {
                const sidebar = document.getElementById('sidebar');
                if (sidebar) {
                    sidebar.classList.toggle('open');
                }
            });
        }
        
        // 点击页面内容区域关闭侧边栏（移动端）
        const pageContent = document.getElementById('pageContent');
        if (pageContent) {
            pageContent.addEventListener('click', () => {
                const sidebar = document.getElementById('sidebar');
                if (sidebar && sidebar.classList.contains('open')) {
                    sidebar.classList.remove('open');
                }
            });
        }
        
        // 监听浏览器前进后退
        window.addEventListener('popstate', (e) => {
            if (e.state?.route && this.routes[e.state.route]) {
                this.currentRoute = e.state.route;
                this.updateUI();
            }
        });
        
        // 初始化页面
        this.updateUI();
    }
};

// 页面加载函数
async function loadKnowledgePage(container) {
    container.innerHTML = `
        <div class="chat-container">
            <div class="chat-messages" id="knowledgeMessages"></div>
            <div class="chat-input-area">
                <div class="input-wrapper">
                    <input type="text" id="knowledgeInput" placeholder="输入您的问题..." autocomplete="off">
                    <button class="send-btn" id="knowledgeSend">➤</button>
                </div>
            </div>
        </div>
    `;
    
    // 初始化聊天
    initKnowledgeChat();
}

async function loadSearchPage(container) {
    container.innerHTML = `
        <div class="chat-container">
            <div class="search-settings-bar">
                <button class="search-settings-toggle" id="searchSettingsToggle">
                    ⚙️ 搜索设置
                </button>
                <div class="search-settings-panel" id="searchSettingsPanel">
                    <div class="search-settings-grid">
                        <div class="search-setting-item">
                            <label class="search-setting-label">返回数量</label>
                            <select id="searchTopK" class="search-setting-select">
                                <option value="5">5条</option>
                                <option value="10" selected>10条</option>
                                <option value="20">20条</option>
                                <option value="50">50条</option>
                            </select>
                        </div>
                        <div class="search-setting-item">
                            <label class="search-setting-label">智能重排</label>
                            <label class="search-setting-switch">
                                <input type="checkbox" id="searchRerank" checked>
                                <span class="search-setting-slider"></span>
                            </label>
                        </div>
                        <div class="search-setting-item">
                            <label class="search-setting-label">质量评估</label>
                            <label class="search-setting-switch">
                                <input type="checkbox" id="searchEvaluation" checked>
                                <span class="search-setting-slider"></span>
                            </label>
                        </div>
                        <div class="search-setting-item">
                            <label class="search-setting-label">用户ID</label>
                            <input type="text" id="searchUserId" class="search-setting-input" placeholder="可选">
                        </div>
                        <div class="search-setting-item">
                            <label class="search-setting-label">会话ID</label>
                            <input type="text" id="searchSessionId" class="search-setting-input" placeholder="可选">
                        </div>
                    </div>
                </div>
            </div>
            <div class="chat-messages" id="searchMessages"></div>
            <div class="chat-input-area">
                <div class="input-wrapper">
                    <input type="text" id="searchInput" placeholder="输入搜索内容..." autocomplete="off">
                    <button class="send-btn" id="searchSend">➤</button>
                </div>
            </div>
        </div>
    `;

    initSearchChat();
}

async function loadPlanPage(container) {
    container.innerHTML = `
        <div class="plan-container">
            <div class="plan-header">
                <h2>📋 计划管理</h2>
            </div>
            <div class="plan-input-section">
                <div class="input-wrapper">
                    <select id="planMode" class="plan-mode-select">
                        <option value="auto">⚡ 自动执行</option>
                        <option value="generate">👁️ 生成预览</option>
                        <option value="workflow">🔄 工作流</option>
                    </select>
                    <input type="text" id="planInput" placeholder="输入您的任务描述..." autocomplete="off">
                    <button class="send-btn" id="planSend">➤</button>
                </div>
            </div>
            <div id="planResults" class="plan-results"></div>
            <div id="planSteps" class="plan-steps"></div>
        </div>
    `;

    initPlanManager();
}

async function loadMemoryPage(container) {
    container.innerHTML = `
        <div>
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2>🧠 记忆管理</h2>
                <button class="btn btn-secondary" id="clearMemoryBtn">清空记忆</button>
            </div>
            <div id="memoryList" class="empty-state">
                <div class="empty-icon">🧠</div>
                <div class="empty-title">暂无记忆数据</div>
                <div class="empty-desc">使用搜索功能后会记录记忆</div>
            </div>
        </div>
    `;
    
    document.getElementById('clearMemoryBtn').addEventListener('click', async () => {
        if (confirm('确定要清空所有记忆吗？')) {
            await api.memory.clear();
            await loadMemories();
        }
    });
    
    await loadMemories();
}

async function loadPlans() {
    const planList = document.getElementById('planList');
    try {
        const plans = await api.plan.list();
        if (plans && plans.length > 0) {
            planList.innerHTML = plans.map(plan => `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: start;">
                        <div>
                            <div class="card-title">${plan.title || '未命名计划'}</div>
                            <div class="card-content">${plan.description || '暂无描述'}</div>
                        </div>
                        <span style="padding: 4px 12px; border-radius: 12px; font-size: 12px; background: ${getStatusColor(plan.status)}; color: white;">
                            ${getStatusText(plan.status)}
                        </span>
                    </div>
                    <div style="margin-top: 12px; display: flex; gap: 8px;">
                        <button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="executePlan('${plan.id}')">执行</button>
                        <button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="deletePlan('${plan.id}')">删除</button>
                    </div>
                </div>
            `).join('');
        }
    } catch (error) {
        console.error('Failed to load plans:', error);
    }
}

function getStatusColor(status) {
    const colors = {
        'PENDING': '#ffc107',
        'RUNNING': '#17a2b8',
        'COMPLETED': '#28a745',
        'FAILED': '#dc3545'
    };
    return colors[status] || '#6c757d';
}

function getStatusText(status) {
    const texts = {
        'PENDING': '待执行',
        'RUNNING': '执行中',
        'COMPLETED': '已完成',
        'FAILED': '失败'
    };
    return texts[status] || status;
}

async function executePlan(id) {
    try {
        await api.plan.execute(id);
        await loadPlans();
    } catch (error) {
        alert('执行失败: ' + error.message);
    }
}

async function deletePlan(id) {
    if (confirm('确定要删除这个计划吗？')) {
        try {
            await api.plan.delete(id);
            await loadPlans();
        } catch (error) {
            alert('删除失败: ' + error.message);
        }
    }
}

function createPlanModal() {
    const modal = document.createElement('div');
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0,0,0,0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
    `;
    
    modal.innerHTML = `
        <div style="background: white; border-radius: 16px; padding: 24px; width: 90%; max-width: 400px;">
            <h3 style="margin-bottom: 20px;">创建新计划</h3>
            <div style="margin-bottom: 16px;">
                <label style="display: block; margin-bottom: 8px; font-size: 14px; font-weight: 500;">计划标题</label>
                <input type="text" id="planTitle" style="width: 100%; padding: 10px; border: 1px solid #e9ecef; border-radius: 8px; outline: none;" placeholder="输入计划标题">
            </div>
            <div style="margin-bottom: 16px;">
                <label style="display: block; margin-bottom: 8px; font-size: 14px; font-weight: 500;">计划描述</label>
                <textarea id="planDesc" style="width: 100%; padding: 10px; border: 1px solid #e9ecef; border-radius: 8px; outline: none; resize: vertical;" rows="3" placeholder="输入计划描述"></textarea>
            </div>
            <div style="display: flex; gap: 12px; justify-content: flex-end;">
                <button class="btn btn-secondary" onclick="this.parentElement.parentElement.parentElement.remove()">取消</button>
                <button class="btn btn-primary" onclick="savePlan()">保存</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
}

async function savePlan() {
    const title = document.getElementById('planTitle').value.trim();
    const desc = document.getElementById('planDesc').value.trim();
    
    if (!title) {
        alert('请输入计划标题');
        return;
    }
    
    try {
        await api.plan.create({ title, description: desc });
        document.querySelector('div[style*="position: fixed"]').remove();
        await loadPlans();
    } catch (error) {
        alert('创建失败: ' + error.message);
    }
}

async function loadMemories() {
    const memoryList = document.getElementById('memoryList');
    try {
        const memories = await api.memory.list();
        if (memories && memories.length > 0) {
            memoryList.innerHTML = memories.map(memory => `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: start;">
                        <div>
                            <div class="card-title">${memory.content || '无内容'}</div>
                            <div class="card-content">
                                <div style="font-size: 12px; color: #adb5bd;">
                                    类型: ${memory.type} | 时间: ${memory.timestamp}
                                </div>
                            </div>
                        </div>
                        <div style="font-size: 12px; color: #6c757d;">
                            重要度: ${(memory.importance || 0).toFixed(2)}
                        </div>
                    </div>
                </div>
            `).join('');
        }
    } catch (error) {
        console.error('Failed to load memories:', error);
    }
}
