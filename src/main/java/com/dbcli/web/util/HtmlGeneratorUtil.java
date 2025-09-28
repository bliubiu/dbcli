package com.dbcli.web.util;

public class HtmlGeneratorUtil {
    
    /**
     * 生成增强版Web管理界面的HTML内容
     * 
     * @return HTML内容字符串
     */
    public static String generateEnhancedDashboardHtml() {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>DBCLI 增强版管理控制台</title>\n" +
            "    <style>\n" +
            "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f7fa; }\n" +
            "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 2rem; text-align: center; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }\n" +
            "        .header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; }\n" +
            "        .header p { font-size: 1.1rem; opacity: 0.9; }\n" +
            "        .container { max-width: 1400px; margin: 2rem auto; padding: 0 1rem; }\n" +
            "        .card { background: white; border-radius: 12px; padding: 2rem; margin-bottom: 2rem; box-shadow: 0 4px 6px rgba(0,0,0,0.07); border: 1px solid #e1e8ed; }\n" +
            "        .card h3 { color: #2c3e50; margin-bottom: 1.5rem; font-size: 1.4rem; display: flex; align-items: center; }\n" +
            "        .card h3::before { content: attr(data-icon); margin-right: 0.5rem; font-size: 1.6rem; }\n" +
            "        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 2rem; }\n" +
            "        .btn { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; padding: 0.8rem 1.5rem; border-radius: 8px; cursor: pointer; margin: 0.5rem; font-size: 1rem; transition: all 0.3s ease; }\n" +
            "        .btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }\n" +
            "        .btn:disabled { background: #bdc3c7; cursor: not-allowed; transform: none; }\n" +
            "        .btn-success { background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%); }\n" +
            "        .btn-warning { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }\n" +
            "        .btn-info { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }\n" +
            "        .btn-secondary { background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%); color: #333; }\n" +
            "        .status-indicator { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; }\n" +
            "        .status-online { background: #27ae60; }\n" +
            "        .status-offline { background: #e74c3c; }\n" +
            "        .status-warning { background: #f39c12; }\n" +
            "        .log-container { background: #2c3e50; color: #ecf0f1; padding: 1.5rem; border-radius: 8px; font-family: 'Consolas', 'Monaco', monospace; max-height: 400px; overflow-y: auto; font-size: 0.9rem; line-height: 1.4; }\n" +
            "        .log-line { margin-bottom: 4px; white-space: pre-wrap; }\n" +
            "        .log-error { color: #e74c3c; }\n" +
            "        .log-warn { color: #f39c12; }\n" +
            "        .log-info { color: #3498db; }\n" +
            "        .log-success { color: #27ae60; }\n" +
            "        .button-group { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 1rem; }\n" +
            "        .metric-card { text-align: center; padding: 1.5rem; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; border-radius: 12px; }\n" +
            "        .metric-value { font-size: 2.5rem; font-weight: bold; margin-bottom: 0.5rem; }\n" +
            "        .metric-label { font-size: 1rem; opacity: 0.9; }\n" +
            "        .progress-bar { width: 100%; height: 8px; background: #ecf0f1; border-radius: 4px; overflow: hidden; margin: 1rem 0; }\n" +
            "        .progress-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); transition: width 0.3s ease; }\n" +
            "        .alert { padding: 1rem; border-radius: 8px; margin: 1rem 0; }\n" +
            "        .alert-info { background: #d1ecf1; color: #0c5460; border-left: 4px solid #17a2b8; }\n" +
            "        .alert-success { background: #d4edda; color: #155724; border-left: 4px solid #28a745; }\n" +
            "        .alert-warning { background: #fff3cd; color: #856404; border-left: 4px solid #ffc107; }\n" +
            "        .alert-error { background: #f8d7da; color: #721c24; border-left: 4px solid #dc3545; }\n" +
            "        .loading { display: none; }\n" +
            "        .loading.show { display: inline-block; }\n" +
            "        .spinner { display: inline-block; width: 20px; height: 20px; border: 3px solid rgba(255,255,255,.3); border-radius: 50%; border-top-color: #fff; animation: spin 1s ease-in-out infinite; }\n" +
            "        @keyframes spin { to { transform: rotate(360deg); } }\n" +
            "        .cooldown-info { background: #fff3cd; color: #856404; padding: 1rem; border-radius: 8px; margin: 1rem 0; border-left: 4px solid #ffc107; }\n" +
            "        .config-list { margin-top: 1rem; }\n" +
            "        .config-item { display: flex; justify-content: space-between; align-items: center; padding: 0.8rem; border: 1px solid #eee; border-radius: 6px; margin-bottom: 0.5rem; }\n" +
            "        .config-item:hover { background: #f8f9fa; }\n" +
            "        .config-actions { display: flex; gap: 0.5rem; }\n" +
            "        .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); }\n" +
            "        .modal-content { background-color: white; margin: 5% auto; padding: 2rem; border-radius: 12px; width: 80%; max-width: 800px; max-height: 80vh; overflow-y: auto; }\n" +
            "        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer; }\n" +
            "        .close:hover { color: black; }\n" +
            "        .form-group { margin-bottom: 1rem; }\n" +
            "        .form-group label { display: block; margin-bottom: 0.5rem; font-weight: bold; }\n" +
            "        .form-group textarea { width: 100%; min-height: 300px; font-family: 'Consolas', 'Monaco', monospace; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }\n" +
            "        .tabs { display: flex; margin-bottom: 1rem; border-bottom: 1px solid #eee; }\n" +
            "        .tab { padding: 1rem 2rem; cursor: pointer; border-bottom: 3px solid transparent; }\n" +
            "        .tab.active { border-bottom: 3px solid #667eea; color: #667eea; font-weight: bold; }\n" +
            "        .tab-content { display: none; }\n" +
            "        .tab-content.active { display: block; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"header\">\n" +
            "        <h1>🚀 DBCLI 增强版管理控制台</h1>\n" +
            "        <p>数据库连接性能测试工具 - 完整功能Web管理界面</p>\n" +
            "    </div>\n" +
            "    \n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"tabs\">\n" +
            "            <div class=\"tab active\" data-tab=\"dashboard\">仪表板</div>\n" +
            "            <div class=\"tab\" data-tab=\"config\">配置管理</div>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div id=\"dashboard-tab\" class=\"tab-content active\">\n" +
            "            <div class=\"grid\">\n" +
            "                <div class=\"card\">\n" +
            "                    <h3 data-icon=\"📊\">系统状态监控</h3>\n" +
            "                    <div class=\"metric-card\">\n" +
            "                        <div class=\"metric-value\" id=\"systemStatus\">\n" +
            "                            <span class=\"status-indicator status-online\"></span>运行中\n" +
            "                        </div>\n" +
            "                        <div class=\"metric-label\">系统运行状态</div>\n" +
            "                    </div>\n" +
            "                    <div class=\"progress-bar\">\n" +
            "                        <div class=\"progress-fill\" style=\"width: 85%\"></div>\n" +
            "                    </div>\n" +
            "                    <p>系统健康度: 85%</p>\n" +
            "                </div>\n" +
            "                \n" +
            "                <div class=\"card\">\n" +
            "                    <h3 data-icon=\"🔗\">数据库连接测试</h3>\n" +
            "                    <div class=\"alert alert-info\">\n" +
            "                        <strong>功能说明:</strong> 测试所有配置的数据库连接，自动重置黑名单，支持频率限制保护。\n" +
            "                    </div>\n" +
            "                    <div id=\"connectionTestResult\"></div>\n" +
            "                    <div class=\"button-group\">\n" +
            "                        <button class=\"btn btn-warning\" onclick=\"testDatabaseConnections()\" id=\"testConnectionBtn\">\n" +
            "                            <span class=\"loading\" id=\"testLoading\"><span class=\"spinner\"></span></span>\n" +
            "                            🔍 测试数据库连接\n" +
            "                        </button>\n" +
            "                    </div>\n" +
            "                    <div id=\"cooldownInfo\" class=\"cooldown-info\" style=\"display: none;\"></div>\n" +
            "                </div>\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"grid\">\n" +
            "                <div class=\"card\">\n" +
            "                    <h3 data-icon=\"🔐\">配置文件加密</h3>\n" +
            "                    <div class=\"alert alert-info\">\n" +
            "                        <strong>安全提示:</strong> 使用SM4算法加密数据库配置文件中的敏感信息，确保密码安全。\n" +
            "                    </div>\n" +
            "                    <div id=\"encryptResult\"></div>\n" +
            "                    <div class=\"button-group\">\n" +
            "                        <button class=\"btn btn-success\" onclick=\"encryptConfigurations()\" id=\"encryptBtn\">\n" +
            "                            <span class=\"loading\" id=\"encryptLoading\"><span class=\"spinner\"></span></span>\n" +
            "                            🔒 加密配置文件\n" +
            "                        </button>\n" +
            "                    </div>\n" +
            "                </div>\n" +
            "                \n" +
            "                <div class=\"card\">\n" +
            "                    <h3 data-icon=\"📈\">报告生成</h3>\n" +
            "                    <div class=\"alert alert-info\">\n" +
            "                        <strong>报告类型:</strong> 支持Excel和HTML格式报告，包含详细的数据库性能指标和分析。\n" +
            "                    </div>\n" +
            "                    <div id=\"reportResult\"></div>\n" +
            "                    <div class=\"button-group\">\n" +
            "                        <button class=\"btn btn-info\" onclick=\"generateReport('excel')\" id=\"excelBtn\">\n" +
            "                            <span class=\"loading\" id=\"excelLoading\"><span class=\"spinner\"></span></span>\n" +
            "                            📊 生成Excel报告\n" +
            "                        </button>\n" +
            "                        <button class=\"btn btn-info\" onclick=\"generateReport('html')\" id=\"htmlBtn\">\n" +
            "                            <span class=\"loading\" id=\"htmlLoading\"><span class=\"spinner\"></span></span>\n" +
            "                            🌐 生成HTML报告\n" +
            "                        </button>\n" +
            "                        <button class=\"btn\" onclick=\"generateReport('both')\" id=\"bothBtn\">\n" +
            "                            <span class=\"loading\" id=\"bothLoading\"><span class=\"spinner\"></span></span>\n" +
            "                            📋 生成全部报告\n" +
            "                        </button>\n" +
            "                    </div>\n" +
            "                </div>\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"card\">\n" +
            "                <h3 data-icon=\"📝\">实时日志监控</h3>\n" +
            "                <div class=\"alert alert-info\">\n" +
            "                    <strong>日志说明:</strong> 实时显示系统运行日志，自动刷新，支持多种日志级别显示。\n" +
            "                </div>\n" +
            "                <div class=\"log-container\" id=\"logContainer\">\n" +
            "                    <div class=\"log-line log-info\">[INFO] Web管理界面已启动</div>\n" +
            "                    <div class=\"log-line log-info\">[INFO] 配置热重载功能已启用</div>\n" +
            "                    <div class=\"log-line log-success\">[SUCCESS] 系统运行正常</div>\n" +
            "                </div>\n" +
            "                <div class=\"button-group\">\n" +
            "                    <button class=\"btn\" onclick=\"refreshLogs()\">🔄 刷新日志</button>\n" +
            "                    <button class=\"btn\" onclick=\"clearLogs()\">🗑️ 清空显示</button>\n" +
            "                </div>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div id=\"config-tab\" class=\"tab-content\">\n" +
            "            <div class=\"card\">\n" +
            "                <h3 data-icon=\"⚙️\">数据库配置管理</h3>\n" +
            "                <div class=\"alert alert-info\">\n" +
            "                    <strong>功能说明:</strong> 查看、编辑和管理数据库配置文件，支持YAML格式。\n" +
            "                </div>\n" +
            "                <div id=\"configResult\"></div>\n" +
            "                <div class=\"button-group\">\n" +
            "                    <button class=\"btn btn-info\" onclick=\"loadConfigList()\">🔄 刷新配置列表</button>\n" +
            "                    <button class=\"btn btn-success\" onclick=\"createNewConfig()\">➕ 新建配置文件</button>\n" +
            "                </div>\n" +
            "                <div class=\"config-list\" id=\"configList\">\n" +
            "                    <!-- 配置文件列表将在这里显示 -->\n" +
            "                </div>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <!-- 配置编辑模态框 -->\n" +
            "    <div id=\"configModal\" class=\"modal\">\n" +
            "        <div class=\"modal-content\">\n" +
            "            <span class=\"close\" onclick=\"closeConfigModal()\">&times;</span>\n" +
            "            <h2 id=\"modalTitle\">编辑配置文件</h2>\n" +
            "            <form id=\"configForm\">\n" +
            "                <input type=\"hidden\" id=\"configFileName\">\n" +
            "                <div class=\"form-group\">\n" +
            "                    <label for=\"configContent\">配置内容 (YAML格式):</label>\n" +
            "                    <textarea id=\"configContent\" placeholder=\"在此输入YAML格式的配置内容...\"></textarea>\n" +
            "                </div>\n" +
            "                <div class=\"button-group\">\n" +
            "                    <button type=\"button\" class=\"btn btn-success\" onclick=\"saveConfig()\">💾 保存配置</button>\n" +
            "                    <button type=\"button\" class=\"btn btn-secondary\" onclick=\"closeConfigModal()\">❌ 取消</button>\n" +
            "                    <button type=\"button\" class=\"btn btn-warning\" onclick=\"deleteConfig()\" id=\"deleteConfigBtn\" style=\"display: none;\">🗑️ 删除配置</button>\n" +
            "                </div>\n" +
            "            </form>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <script>\n" +
            "        let logRefreshInterval;\n" +
            "        let lastConnectionTestTime = 0;\n" +
            "        const CONNECTION_TEST_COOLDOWN = 10 * 60 * 1000; // 10分钟\n" +
            "        \n" +
            "        // 页面加载完成后启动定时刷新\n" +
            "        document.addEventListener('DOMContentLoaded', function() {\n" +
            "            refreshLogs();\n" +
            "            startLogRefresh();\n" +
            "            \n" +
            "            // 添加标签页切换功能\n" +
            "            document.querySelectorAll('.tab').forEach(tab => {\n" +
            "                tab.addEventListener('click', function() {\n" +
            "                    // 移除所有活动状态\n" +
            "                    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n" +
            "                    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));\n" +
            "                    \n" +
            "                    // 激活当前标签页\n" +
            "                    this.classList.add('active');\n" +
            "                    const tabName = this.getAttribute('data-tab');\n" +
            "                    document.getElementById(tabName + '-tab').classList.add('active');\n" +
            "                    \n" +
            "                    // 如果是配置管理标签页，加载配置列表\n" +
            "                    if (tabName === 'config') {\n" +
            "                        loadConfigList();\n" +
            "                    }\n" +
            "                });\n" +
            "            });\n" +
            "        });\n" +
            "        \n" +
            "        function startLogRefresh() {\n" +
            "            logRefreshInterval = setInterval(refreshLogs, 3000); // 每3秒刷新一次\n" +
            "        }\n" +
            "        \n" +
            "        function stopLogRefresh() {\n" +
            "            if (logRefreshInterval) {\n" +
            "                clearInterval(logRefreshInterval);\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function refreshLogs() {\n" +
            "            fetch('/api/logs')\n" +
            "                .then(response => response.json())\n" +
            "                .then(data => {\n" +
            "                    const logContainer = document.getElementById('logContainer');\n" +
            "                    logContainer.innerHTML = ''; // 清空日志容器\n" +
            "                    \n" +
            "                    // 修复日志数据显示逻辑\n" +
            "                    // 后端返回的是 {\"logs\": [\"日志行1\", \"日志行2\", ...]} 格式\n" +
            "                    const logs = data.logs || [];\n" +
            "                    logs.forEach(logLine => {\n" +
            "                        const logElement = document.createElement('div');\n" +
            "                        logElement.className = 'log-line';\n" +
            "                        \n" +
            "                        // 根据日志内容设置不同的样式类\n" +
            "                        if (logLine.includes('[ERROR]') || logLine.includes('[error]')) {\n" +
            "                            logElement.classList.add('log-error');\n" +
            "                        } else if (logLine.includes('[WARN]') || logLine.includes('[warn]')) {\n" +
            "                            logElement.classList.add('log-warn');\n" +
            "                        } else if (logLine.includes('[SUCCESS]') || logLine.includes('[success]')) {\n" +
            "                            logElement.classList.add('log-success');\n" +
            "                        } else {\n" +
            "                            logElement.classList.add('log-info');\n" +
            "                        }\n" +
            "                        \n" +
            "                        logElement.textContent = logLine;\n" +
            "                        logContainer.appendChild(logElement);\n" +
            "                    });\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    console.error('加载日志失败:', error);\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function clearLogs() {\n" +
            "            document.getElementById('logContainer').innerHTML = '';\n" +
            "        }\n" +
            "        \n" +
            "        function testDatabaseConnections() {\n" +
            "            const testBtn = document.getElementById('testConnectionBtn');\n" +
            "            const testLoading = document.getElementById('testLoading');\n" +
            "            const cooldownInfo = document.getElementById('cooldownInfo');\n" +
            "            const currentTime = Date.now();\n" +
            "            \n" +
            "            if (currentTime - lastConnectionTestTime < CONNECTION_TEST_COOLDOWN) {\n" +
            "                cooldownInfo.style.display = 'block';\n" +
            "                cooldownInfo.textContent = `请等待 ${Math.ceil((CONNECTION_TEST_COOLDOWN - (currentTime - lastConnectionTestTime)) / 1000)} 秒后重试`; \n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            testBtn.disabled = true;\n" +
            "            testLoading.classList.add('show');\n" +
            "            cooldownInfo.style.display = 'none';\n" +
            "            \n" +
            "            fetch('/api/connection-test', { method: 'POST' })\n" +
            "                .then(response => response.json())\n" +
            "                .then(data => {\n" +
            "                    let resultHtml = '';\n" +
            "                    if (data.error) {\n" +
            "                        resultHtml = `<div class=\"alert alert-error\"><strong>错误:</strong> ${data.error}</div>`;\n" +
            "                    } else {\n" +
            "                        const successRate = data.total > 0 ? Math.round((data.success / data.total) * 100) : 0;\n" +
            "                        resultHtml = `\n" +
            "                            <div class=\"alert alert-success\">\n" +
            "                                <h4>🎉 连接测试完成</h4>\n" +
            "                                <p><strong>成功连接:</strong> ${data.success} 个数据库</p>\n" +
            "                                <p><strong>连接失败:</strong> ${data.failed} 个数据库</p>\n" +
            "                                <p><strong>总计测试:</strong> ${data.total} 个数据库</p>\n" +
            "                                <p><strong>成功率:</strong> ${successRate}%</p>\n" +
            "                            </div>\n" +
            "                        `;\n" +
            "                    }\n" +
            "                    document.getElementById('connectionTestResult').innerHTML = resultHtml;\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    document.getElementById('connectionTestResult').innerHTML = `<div class=\"alert alert-error\"><strong>网络错误:</strong> ${error.message}</div>`;\n" +
            "                })\n" +
            "                .finally(() => {\n" +
            "                    testBtn.disabled = false;\n" +
            "                    testLoading.classList.remove('show');\n" +
            "                    lastConnectionTestTime = currentTime;\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function encryptConfigurations() {\n" +
            "            const encryptBtn = document.getElementById('encryptBtn');\n" +
            "            const encryptLoading = document.getElementById('encryptLoading');\n" +
            "            \n" +
            "            encryptBtn.disabled = true;\n" +
            "            encryptLoading.classList.add('show');\n" +
            "            \n" +
            "            fetch('/api/encrypt-config', { method: 'POST' })\n" +
            "                .then(response => response.json())\n" +
            "                .then(data => {\n" +
            "                    let resultHtml = '';\n" +
            "                    if (data.success) {\n" +
            "                        resultHtml = `\n" +
            "                            <div class=\"alert alert-success\">\n" +
            "                                <h4>🔒 配置加密成功</h4>\n" +
            "                                <p>${data.message}</p>\n" +
            "                                <p><small>所有敏感信息已使用SM4算法安全加密</small></p>\n" +
            "                            </div>\n" +
            "                        `;\n" +
            "                    } else {\n" +
            "                        resultHtml = `<div class=\"alert alert-error\"><strong>加密失败:</strong> ${data.message}</div>`;\n" +
            "                    }\n" +
            "                    document.getElementById('encryptResult').innerHTML = resultHtml;\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    document.getElementById('encryptResult').innerHTML = `<div class=\"alert alert-error\"><strong>网络错误:</strong> ${error.message}</div>`;\n" +
            "                })\n" +
            "                .finally(() => {\n" +
            "                    encryptBtn.disabled = false;\n" +
            "                    encryptLoading.classList.remove('show');\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function generateReport(type) {\n" +
            "            const excelBtn = document.getElementById('excelBtn');\n" +
            "            const htmlBtn = document.getElementById('htmlBtn');\n" +
            "            const bothBtn = document.getElementById('bothBtn');\n" +
            "            const excelLoading = document.getElementById('excelLoading');\n" +
            "            const htmlLoading = document.getElementById('htmlLoading');\n" +
            "            const bothLoading = document.getElementById('bothLoading');\n" +
            "            \n" +
            "            // 禁用所有按钮\n" +
            "            excelBtn.disabled = true;\n" +
            "            htmlBtn.disabled = true;\n" +
            "            bothBtn.disabled = true;\n" +
            "            \n" +
            "            // 只显示对应按钮的加载状态\n" +
            "            if (type === 'excel') {\n" +
            "                excelLoading.classList.add('show');\n" +
            "            } else if (type === 'html') {\n" +
            "                htmlLoading.classList.add('show');\n" +
            "            } else if (type === 'both') {\n" +
            "                bothLoading.classList.add('show');\n" +
            "            }\n" +
            "            \n" +
            "            fetch('/api/generate-report', {\n" +
            "                method: 'POST',\n" +
            "                headers: {\n" +
            "                    'Content-Type': 'application/json'\n" +
            "                },\n" +
            "                body: JSON.stringify({ type: type })\n" +
            "            })\n" +
            "                .then(response => response.json())\n" +
            "                .then(data => {\n" +
            "                    let resultHtml = '';\n" +
            "                    if (data.success) {\n" +
            "                        resultHtml = `\n" +
            "                            <div class=\"alert alert-success\">\n" +
            "                                <h4>📊 报告生成成功</h4>\n" +
            "                                <p><strong>保存位置:</strong> ${data.message}</p>\n" +
            "                                <p><strong>文件名:</strong> ${data.fileName}</p>\n" +
            "                        `;\n" +
            "                        // 只有在生成HTML报告或全部报告时才显示预览按钮\n" +
            "                        if (data.previewUrl && (type === 'html' || type === 'both')) {\n" +
            "                            resultHtml += `<p><a href=\"${data.previewUrl}\" target=\"_blank\" class=\"btn btn-info\">🌐 预览HTML报告</a></p>`;\n" +
            "                        }\n" +
            "                        resultHtml += `</div>`;\n" +
            "                    } else {\n" +
            "                        resultHtml = `<div class=\"alert alert-error\"><strong>生成失败:</strong> ${data.message}</div>`;\n" +
            "                    }\n" +
            "                    document.getElementById('reportResult').innerHTML = resultHtml;\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    document.getElementById('reportResult').innerHTML = `<div class=\"alert alert-error\"><strong>网络错误:</strong> ${error.message}</div>`;\n" +
            "                })\n" +
            "                .finally(() => {\n" +
            "                    excelBtn.disabled = false;\n" +
            "                    htmlBtn.disabled = false;\n" +
            "                    bothBtn.disabled = false;\n" +
            "                    excelLoading.classList.remove('show');\n" +
            "                    htmlLoading.classList.remove('show');\n" +
            "                    bothLoading.classList.remove('show');\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function loadConfigList() {\n" +
            "            fetch('/api/config')\n" +
            "                .then(response => response.json())\n" +
            "                .then(data => {\n" +
            "                    const configList = document.getElementById('configList');\n" +
            "                    configList.innerHTML = ''; // 清空配置列表\n" +
            "                    data.forEach(config => {\n" +
            "                        const configItem = document.createElement('div');\n" +
            "                        configItem.className = 'config-item';\n" +
            "                        configItem.innerHTML = `\n" +
            "                            <span>${config.name}</span>\n" +
            "                            <div class=\"config-actions\">\n" +
            "                                <button class=\"btn btn-secondary\" onclick=\"editConfig('${config.name}')\">编辑</button>\n" +
            "                                <button class=\"btn btn-warning\" onclick=\"deleteConfig('${config.name}')\">删除</button>\n" +
            "                            </div>\n" +
            "                        `;\n" +
            "                        configList.appendChild(configItem);\n" +
            "                    });\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    console.error('加载配置列表失败:', error);\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function createNewConfig() {\n" +
            "            document.getElementById('configFileName').value = '';\n" +
            "            document.getElementById('configContent').value = '';\n" +
            "            document.getElementById('modalTitle').textContent = '新建配置文件';\n" +
            "            document.getElementById('deleteConfigBtn').style.display = 'none';\n" +
            "            document.getElementById('configModal').style.display = 'block';\n" +
            "        }\n" +
            "        \n" +
            "        function editConfig(fileName) {\n" +
            "            console.log('编辑配置文件:', fileName);\n" +
            "            \n" +
            "            fetch(`/api/config/${fileName}`)\n" +
            "                .then(response => {\n" +
            "                    if (!response.ok) {\n" +
            "                        throw new Error(`HTTP error! status: ${response.status}`);\n" +
            "                    }\n" +
            "                    return response.json();\n" +
            "                })\n" +
            "                .then(data => {\n" +
            "                    console.log('加载配置文件成功:', data);\n" +
            "                    document.getElementById('configFileName').value = fileName;\n" +
            "                    // 处理返回的数据格式\n" +
            "                    const content = data.content || data;\n" +
            "                    document.getElementById('configContent').value = content;\n" +
            "                    document.getElementById('modalTitle').textContent = `编辑配置文件: ${fileName}`;\n" +
            "                    document.getElementById('deleteConfigBtn').style.display = 'inline-block';\n" +
            "                    document.getElementById('configModal').style.display = 'block';\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    console.error('加载配置文件失败:', error);\n" +
            "                    alert('加载配置文件失败: ' + error.message);\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function saveConfig() {\n" +
            "            const fileName = document.getElementById('configFileName').value;\n" +
            "            const content = document.getElementById('configContent').value;\n" +
            "            \n" +
            "            // 如果是新建配置，需要提示输入文件名\n" +
            "            let actualFileName = fileName;\n" +
            "            if (!actualFileName) {\n" +
            "                actualFileName = prompt('请输入配置文件名（例如：test-config.yml）:');\n" +
            "                if (!actualFileName) {\n" +
            "                    alert('请输入有效的文件名');\n" +
            "                    return;\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            // 确定文件类型\n" +
            "            let fileType = 'database';\n" +
            "            if (actualFileName.toLowerCase().includes('metrics')) {\n" +
            "                fileType = 'metrics';\n" +
            "            }\n" +
            "            \n" +
            "            const requestData = {\n" +
            "                fileName: actualFileName,\n" +
            "                content: content,\n" +
            "                type: fileType\n" +
            "            };\n" +
            "            \n" +
            "            console.log('发送配置保存请求:', requestData);\n" +
            "            \n" +
            "            fetch('/api/config', {\n" +
            "                method: 'POST',\n" +
            "                headers: {\n" +
            "                    'Content-Type': 'application/json'\n" +
            "                },\n" +
            "                body: JSON.stringify(requestData)\n" +
            "            })\n" +
            "                .then(response => {\n" +
            "                    if (!response.ok) {\n" +
            "                        throw new Error(`HTTP error! status: ${response.status}`);\n" +
            "                    }\n" +
            "                    return response.json();\n" +
            "                })\n" +
            "                .then(data => {\n" +
            "                    console.log('配置保存成功:', data);\n" +
            "                    if (data.success) {\n" +
            "                        alert('配置保存成功！');\n" +
            "                        closeConfigModal();\n" +
            "                        loadConfigList();\n" +
            "                    } else {\n" +
            "                        alert('配置保存失败: ' + (data.message || '未知错误'));\n" +
            "                    }\n" +
            "                })\n" +
            "                .catch(error => {\n" +
            "                    console.error('保存配置失败:', error);\n" +
            "                    alert('保存配置失败: ' + error.message);\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        function deleteConfig(fileName) {\n" +
            "            if (confirm('确定要删除该配置文件吗？')) {\n" +
            "                fetch(`/api/config/${fileName}`, { method: 'DELETE' })\n" +
            "                    .then(response => response.json())\n" +
            "                    .then(data => {\n" +
            "                        console.log('配置删除成功:', data);\n" +
            "                        closeConfigModal();\n" +
            "                        loadConfigList();\n" +
            "                    })\n" +
            "                    .catch(error => {\n" +
            "                        console.error('删除配置失败:', error);\n" +
            "                    });\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function closeConfigModal() {\n" +
            "            document.getElementById('configModal').style.display = 'none';\n" +
            "        }\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";
    }
}