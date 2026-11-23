package com.arslanca.orchestrai.api.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendForwardController {

    @GetMapping({"/", "/login", "/pricing", "/docs", "/auth/github/callback", "/auth/google/callback", "/dashboard", "/dashboard/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
