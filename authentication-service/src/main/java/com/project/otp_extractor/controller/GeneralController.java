package com.project.otp_extractor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GeneralController {

    @GetMapping("/home")
    public String print(){
        return "Hello from secured endpoint";
    }

    @GetMapping("/try")
    public String print2(){
        return "Hello from /try endpoint";
    }
}
