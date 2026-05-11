// API调用层
const API_BASE = '/api';

const api = {
    // 知识检索
    knowledge: {
        query: async (data) => {
            const response = await fetch(`${API_BASE}/knowledge/query`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            return response.json();
        }
    },
    
    // AI搜索
    search: {
        query: async (data) => {
            const response = await fetch(`${API_BASE}/ai-search/query`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            return response.json();
        },
        
        ask: async (params) => {
            const response = await fetch(`${API_BASE}/ai-search/ask`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(params)
            });
            return response.json();
        },
        
        analyze: async (query) => {
            const url = new URL(`${API_BASE}/ai-search/analyze`);
            url.searchParams.append('query', query);
            const response = await fetch(url);
            return response.json();
        },
        
        reload: async () => {
            const response = await fetch(`${API_BASE}/ai-search/reload`, { method: 'POST' });
            return response.json();
        }
    },
    
    // 计划管理
    plan: {
        get: async (planId) => {
            const response = await fetch(`${API_BASE}/plan/get/${planId}`);
            return response.json();
        },

        list: async () => {
            const response = await fetch(`${API_BASE}/plan/list`);
            return response.json();
        },

        create: async (data) => {
            const response = await fetch(`${API_BASE}/plan/create`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            return response.json();
        },

        execute: async (id) => {
            const response = await fetch(`${API_BASE}/plan/execute/${id}`);
            return response.json();
        },

        status: async (id) => {
            const response = await fetch(`${API_BASE}/plan/status/${id}`);
            return response.json();
        },

        delete: async (id) => {
            const response = await fetch(`${API_BASE}/plan/remove/${id}`, { method: 'DELETE' });
            return response.json();
        },

        steps: async (planId) => {
            const response = await fetch(`${API_BASE}/plan/steps/${planId}`);
            return response.json();
        },

        auto: async (request) => {
            const response = await fetch(`${API_BASE}/plan/auto`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ request })
            });
            return response.json();
        },

        generate: async (request) => {
            const response = await fetch(`${API_BASE}/plan/generate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ request })
            });
            return response.json();
        },

        workflow: async (data) => {
            const response = await fetch(`${API_BASE}/plan/workflow`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            return response.json();
        }
    },
    
    // 记忆系统
    memory: {
        list: async () => {
            const response = await fetch(`${API_BASE}/memory/list`);
            return response.json();
        },
        
        clear: async () => {
            const response = await fetch(`${API_BASE}/memory/clear`, { method: 'DELETE' });
            return response.json();
        }
    }
};
