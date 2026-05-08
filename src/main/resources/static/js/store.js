// 状态管理
const store = {
    currentUser: null,
    currentPage: 'knowledge',
    searchHistory: [],
    conversationHistory: {},
    planList: [],
    
    setCurrentPage(page) {
        this.currentPage = page;
    },
    
    addSearchHistory(query) {
        if (!this.searchHistory.includes(query)) {
            this.searchHistory.unshift(query);
            if (this.searchHistory.length > 10) {
                this.searchHistory.pop();
            }
        }
        this.saveToLocalStorage();
    },
    
    addMessage(page, message) {
        if (!this.conversationHistory[page]) {
            this.conversationHistory[page] = [];
        }
        this.conversationHistory[page].push(message);
        this.saveToLocalStorage();
    },
    
    getMessages(page) {
        return this.conversationHistory[page] || [];
    },
    
    clearMessages(page) {
        this.conversationHistory[page] = [];
        this.saveToLocalStorage();
    },
    
    saveToLocalStorage() {
        try {
            localStorage.setItem('agent-store', JSON.stringify({
                searchHistory: this.searchHistory,
                conversationHistory: this.conversationHistory
            }));
        } catch (e) {
            console.warn('Failed to save to localStorage', e);
        }
    },
    
    loadFromLocalStorage() {
        try {
            const saved = localStorage.getItem('agent-store');
            if (saved) {
                const data = JSON.parse(saved);
                this.searchHistory = data.searchHistory || [];
                this.conversationHistory = data.conversationHistory || {};
            }
        } catch (e) {
            console.warn('Failed to load from localStorage', e);
        }
    }
};

// 初始化时加载本地存储
store.loadFromLocalStorage();
