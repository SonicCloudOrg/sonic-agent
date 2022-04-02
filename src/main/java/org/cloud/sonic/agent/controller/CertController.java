package org.cloud.sonic.agent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author ZhouYiXun
 * @des 证书下载页
 * @date 2022/3/10 23:04
 */
@Controller
public class CertController {
    @RequestMapping("/assets/download")
    public String download(Model model) {
        model.addAttribute("msg", "欢迎来到证书下载页面");
        model.addAttribute("pemMsg", "👉 点击下载pem证书");
        model.addAttribute("pemName", "sonic-go-mitmproxy-ca-cert.pem");
        model.addAttribute("pemUrl", "/download/sonic-go-mitmproxy-ca-cert.pem");
        model.addAttribute("tips", "如果pem证书无效，请尝试cer证书");
        model.addAttribute("cerMsg", "👉 点击下载cer证书");
        model.addAttribute("cerName", "sonic-go-mitmproxy-ca-cert.cer");
        model.addAttribute("cerUrl", "/download/sonic-go-mitmproxy-ca-cert.cer");
        return "download";
    }
}
