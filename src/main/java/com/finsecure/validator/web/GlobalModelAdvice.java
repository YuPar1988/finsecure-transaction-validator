package com.finsecure.validator.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.finsecure.validator.config.AppProperties;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    private final AppProperties app;

    public GlobalModelAdvice(AppProperties app) {
        this.app = app;
    }

    @ModelAttribute("appInfo")
    public AppProperties appInfo() {
        return app;
    }
}
