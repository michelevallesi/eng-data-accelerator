package org.eda.simulator.teamcenter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhoAmIController {

    @GetMapping("/whoami")
    public Map<String, Object> whoAmI(HttpServletRequest request) {
        Object user = request.getAttribute(IdentityPropagationFilter.REQUEST_ATTR);
        return Map.of("user", user == null ? IdentityPropagationFilter.ANONYMOUS : user);
    }
}
