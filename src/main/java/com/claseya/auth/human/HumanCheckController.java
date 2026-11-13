package com.claseya.auth.human;

import com.claseya.security.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/human")
public class HumanCheckController {

    private final HumanCheckService humanCheckService;
    private final AuthCookieFactory cookieFactory;

    public HumanCheckController(HumanCheckService humanCheckService, AuthCookieFactory cookieFactory) {
        this.humanCheckService = humanCheckService;
        this.cookieFactory = cookieFactory;
    }

    @GetMapping("/status")
    public HumanStatusResponse status(
            @CookieValue(name = AuthCookieFactory.HUMAN_COOKIE, required = false) String cookie) {
        return new HumanStatusResponse(
                humanCheckService.isRequired(),
                humanCheckService.isVerified(cookie));
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody HumanVerifyRequest request,
                                       HttpServletResponse response) {
        String token = humanCheckService.verify(request.token());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieFactory.humanCheck(token, humanCheckService.ttl()).toString());
        return ResponseEntity.noContent().build();
    }
}
