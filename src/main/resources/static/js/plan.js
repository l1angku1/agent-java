// 计划管理功能
function initPlanManager() {
    const input = document.getElementById('planInput');
    const sendBtn = document.getElementById('planSend');
    const modeSelect = document.getElementById('planMode');
    const resultsArea = document.getElementById('planResults');
    const planStepsArea = document.getElementById('planSteps');

    const MAX_INPUT_LENGTH = 258000;

    let isLoading = false;
    let currentPlanId = null;

    sendBtn.addEventListener('click', () => executePlan());
    input.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') executePlan();
    });

    modeSelect.addEventListener('change', () => {
        resetPage();
    });

    async function executePlan() {
        const request = input.value.trim();
        
        // 客户端验证输入长度
        if (!request || isLoading) return;
        if (request.length > MAX_INPUT_LENGTH) {
            showError(`输入内容过长，最多允许${MAX_INPUT_LENGTH}个字符`);
            return;
        }

        const mode = modeSelect.value;
        showLoading();
        isLoading = true;
        sendBtn.disabled = true;

        try {
            let response;
            switch (mode) {
                case 'auto':
                    response = await api.plan.auto(request);
                    break;
                case 'generate':
                    response = await api.plan.generate(request);
                    break;
                case 'workflow':
                    response = await api.plan.workflow({ mode: 'preview', request });
                    if (response.success && response.planId) {
                        currentPlanId = response.planId;
                        showWorkflowActions(response);
                        isLoading = false;
                        sendBtn.disabled = false;
                        return;
                    }
                    break;
            }

            if (response.success) {
                showSuccess(response, mode);
            } else {
                showError(response.error || '操作失败');
            }
        } catch (error) {
            showError('网络连接失败，请稍后重试。');
        }

        isLoading = false;
        sendBtn.disabled = false;
    }

    async function confirmPlan() {
        if (!currentPlanId) return;

        const confirmBtn = document.getElementById('confirmPlanBtn');
        const cancelBtn = document.getElementById('cancelPlanBtn');
        confirmBtn.disabled = true;
        cancelBtn.disabled = true;
        sendBtn.disabled = true;
        confirmBtn.textContent = '执行中...';

        resultsArea.innerHTML = `
            <div class="result-pending">
                <div class="result-icon">🔄</div>
                <div class="result-message">计划执行中...</div>
            </div>
        `;

        try {
            const response = await api.plan.workflow({ mode: 'confirm', planId: currentPlanId });
            if (response.success) {
                showSuccess({ message: '计划执行完成', plan: response.plan }, 'workflow');
            } else {
                showError(response.error || '执行失败');
            }
            hideWorkflowActions();
        } catch (error) {
            showError('网络连接失败，请稍后重试。');
        }

        confirmBtn.disabled = false;
        cancelBtn.disabled = false;
        sendBtn.disabled = false;
        confirmBtn.textContent = '确认执行';
    }

    async function cancelPlan() {
        if (!currentPlanId) return;

        const confirmBtn = document.getElementById('confirmPlanBtn');
        const cancelBtn = document.getElementById('cancelPlanBtn');
        confirmBtn.disabled = true;
        cancelBtn.disabled = true;
        sendBtn.disabled = true;

        try {
            const response = await api.plan.workflow({ mode: 'cancel', planId: currentPlanId });
            if (response.success) {
                showInfo('计划已取消');
            } else {
                showError(response.error || '取消失败');
            }
            hideWorkflowActions();
        } catch (error) {
            showError('网络连接失败，请稍后重试。');
        }

        confirmBtn.disabled = false;
        cancelBtn.disabled = false;
        sendBtn.disabled = false;
    }

    function showLoading() {
        resultsArea.innerHTML = `
            <div class="loading">
                <span class="loading-spinner"></span>
                <span>处理中...</span>
            </div>
        `;
        planStepsArea.innerHTML = '';
    }

    function showSuccess(data, mode) {
        const plan = data.plan || data.generatedPlan;
        const message = data.message || '操作成功';

        console.log('showSuccess called with:', { mode, hasPlan: !!plan, data });

        let finalResult = '';
        
        if (plan) {
            if (plan.finalAnswer) {
                finalResult = plan.finalAnswer;
            } else if (plan.steps && plan.steps.length > 0) {
                // 优先显示步骤结果（更清晰，有步骤名称）
                const completedSteps = plan.steps.filter(s => s.result);
                if (completedSteps.length > 0) {
                    if (completedSteps.length === 1) {
                        finalResult = completedSteps[0].result;
                    } else {
                        finalResult = completedSteps.map((step, index) => {
                            return `${step.name || `步骤${index + 1}`}:\n${step.result}`;
                        }).join('\n\n');
                    }
                }
            } else if (plan.context) {
                // 如果没有步骤结果，再显示 context 中的输出
                const outputs = Object.values(plan.context).filter(v => v && String(v).trim());
                if (outputs.length > 0) {
                    finalResult = outputs.length === 1 
                        ? String(outputs[0]) 
                        : outputs.join('\n\n');
                }
            }
        }

        let resultHtml = `
            <div class="result-success">
                <div class="result-icon">✅</div>
                <div class="result-message">${message}</div>
            </div>
        `;

        if (finalResult) {
            resultHtml += `
                <div class="final-result">
                    <div class="result-title">📝 执行结果</div>
                    <div class="result-content">${escapeHtml(finalResult)}</div>
                </div>
            `;
        }

        resultsArea.innerHTML = resultHtml;

        if (mode !== 'auto') {
            console.log('Showing steps for mode:', mode);
            if (plan && plan.steps) {
                renderPlanSteps(plan.steps);
            } else if (data.steps) {
                renderPlanSteps(data.steps);
            }
        } else {
            console.log('Hiding steps for auto mode');
            planStepsArea.innerHTML = '';
        }
    }

    function showError(message) {
        resultsArea.innerHTML = `
            <div class="result-error">
                <div class="result-icon">❌</div>
                <div class="result-message">${escapeHtml(message)}</div>
            </div>
        `;
        planStepsArea.innerHTML = '';
    }

    function showInfo(message) {
        resultsArea.innerHTML = `
            <div class="result-info">
                <div class="result-icon">ℹ️</div>
                <div class="result-message">${escapeHtml(message)}</div>
            </div>
        `;
        planStepsArea.innerHTML = '';
    }

    function showWorkflowActions(response) {
        const plan = response.plan;
        const steps = response.steps || [];

        resultsArea.innerHTML = `
            <div class="result-pending">
                <div class="result-icon">⏳</div>
                <div class="result-message">计划已生成，等待确认</div>
            </div>
        `;

        renderPlanSteps(steps);

        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'workflow-actions';
        actionsDiv.id = 'workflowActions';
        actionsDiv.innerHTML = `
            <button class="btn btn-primary" id="confirmPlanBtn">确认执行</button>
            <button class="btn btn-secondary" id="cancelPlanBtn">取消</button>
        `;
        resultsArea.appendChild(actionsDiv);

        document.getElementById('confirmPlanBtn').addEventListener('click', confirmPlan);
        document.getElementById('cancelPlanBtn').addEventListener('click', cancelPlan);
    }

    function hideWorkflowActions() {
        const actions = document.getElementById('workflowActions');
        if (actions) actions.remove();
        currentPlanId = null;
    }

    function renderPlanSteps(steps) {
        if (!steps || steps.length === 0) {
            planStepsArea.innerHTML = '';
            return;
        }

        planStepsArea.innerHTML = `
            <div class="steps-container">
                <h3 class="steps-title">📋 计划步骤</h3>
                <div class="steps-list">
                    ${steps.map((step, index) => `
                        <div class="step-item">
                            <div class="step-number">${index + 1}</div>
                            <div class="step-content">
                                <div class="step-name">${escapeHtml(step.name || step.title || '步骤 ' + (index + 1))}</div>
                                ${step.description ? `<div class="step-desc">${escapeHtml(step.description)}</div>` : ''}
                                ${step.status ? `<div class="step-status status-${step.status.toLowerCase()}">${getStatusText(step.status)}</div>` : ''}
                                ${step.result ? `
                                    <div class="step-result">
                                        <div class="result-label">执行结果:</div>
                                        <div class="result-text">${escapeHtml(step.result)}</div>
                                    </div>
                                ` : ''}
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    function getStatusText(status) {
        const statusMap = {
            'PENDING': '待执行',
            'RUNNING': '执行中',
            'COMPLETED': '已完成',
            'FAILED': '失败',
            'CANCELLED': '已取消',
            'PLANNING': '待确认'
        };
        return statusMap[status] || status;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function resetPage() {
        input.value = '';
        resultsArea.innerHTML = '';
        planStepsArea.innerHTML = '';
        isLoading = false;
        sendBtn.disabled = false;
        currentPlanId = null;
        showWelcome();
    }

    showWelcome();
}

function showWelcome() {
    const resultsArea = document.getElementById('planResults');
    const planStepsArea = document.getElementById('planSteps');

    resultsArea.innerHTML = `
        <div class="welcome">
            <div class="welcome-icon">📋</div>
            <h2>欢迎使用计划管理</h2>
            <p>我可以帮您自动分解任务、生成执行计划，并支持确认后执行。</p>
            <div class="mode-intro">
                <div class="mode-card">
                    <div class="mode-icon">⚡</div>
                    <div class="mode-title">自动执行</div>
                    <div class="mode-desc">输入任务 → 自动分解 → 直接执行</div>
                </div>
                <div class="mode-card">
                    <div class="mode-icon">👁️</div>
                    <div class="mode-title">生成预览</div>
                    <div class="mode-desc">输入任务 → 只生成计划 → 预览步骤</div>
                </div>
                <div class="mode-card">
                    <div class="mode-icon">🔄</div>
                    <div class="mode-title">工作流</div>
                    <div class="mode-desc">生成 → 确认 → 执行（可控）</div>
                </div>
            </div>
        </div>
    `;
    planStepsArea.innerHTML = '';
}