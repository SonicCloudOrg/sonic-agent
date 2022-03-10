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
        return "download";
    }
}
