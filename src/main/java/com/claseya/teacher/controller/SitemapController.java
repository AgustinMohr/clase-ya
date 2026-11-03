package com.claseya.teacher.controller;

import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.teacher.service.SitemapService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * SEO-001: public sitemap endpoints. They live outside {@code /api} on purpose: crawlers discover
 * them from {@code robots.txt}, and Vercel proxies only these paths to the backend.
 */
@RestController
public class SitemapController {

    private static final MediaType XML_UTF8 = MediaType.parseMediaType("application/xml;charset=UTF-8");

    private final SitemapService sitemapService;

    public SitemapController(SitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> index() {
        return xml(sitemapService.index());
    }

    @GetMapping(value = "/sitemaps/static.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> staticSitemap() {
        return xml(sitemapService.staticSitemap());
    }

    @GetMapping(value = "/sitemaps/teachers-{page}.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> teachers(@PathVariable String page) {
        int chunk;
        try {
            chunk = Integer.parseInt(page);
        } catch (NumberFormatException ex) {
            throw new ResourceNotFoundException();
        }
        return xml(sitemapService.teachers(chunk));
    }

    private ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok()
                .contentType(XML_UTF8)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()
                        .sMaxAge(Duration.ofHours(24)))
                .body(body);
    }
}
