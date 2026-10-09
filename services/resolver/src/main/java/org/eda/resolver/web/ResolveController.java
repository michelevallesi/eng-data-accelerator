package org.eda.resolver.web;

import org.eda.resolver.domain.ResolvedUri;
import org.eda.resolver.domain.ResolverService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResolveController {

    private final ResolverService service;
    private final String baseDomain;

    public ResolveController(ResolverService service,
                             @Value("${eda.base-domain}") String baseDomain) {
        this.service = service;
        this.baseDomain = stripTrailingSlash(baseDomain);
    }

    @GetMapping("/resolve")
    public ResponseEntity<ResolveResponse> resolve(@RequestParam("uri") String uri) {
        ResolvedUri r = service.resolve(uri);
        HttpHeaders headers = new HttpHeaders();
        if (r.isAliased()) {
            headers.add(HttpHeaders.LINK, "<" + r.canonicalUri() + ">; rel=\"canonical\"");
        }
        return new ResponseEntity<>(
                new ResolveResponse(r.uri(), r.tool(), r.nativeId(), r.deepLink(), r.canonicalUri()),
                headers,
                org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/r/{toolNs}/{id}")
    public ResponseEntity<ResolveResponse> resolveByPath(@PathVariable("toolNs") String toolNs,
                                                         @PathVariable("id") String id) {
        String uri = baseDomain + "/r/" + toolNs + "/" + id;
        return resolve(uri);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public record ResolveResponse(String uri, String tool, String nativeId, String deepLink, String canonicalUri) {
    }
}
