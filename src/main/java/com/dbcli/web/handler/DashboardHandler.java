package com.dbcli.web.handler;

import com.dbcli.web.util.HtmlGeneratorUtil;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * 首页仪表盘页面处理器
 */
public class DashboardHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String response = HtmlGeneratorUtil.generateEnhancedDashboardHtml();
        ResponseUtil.sendResponse(exchange, 200, response, "text/html");
    }
}