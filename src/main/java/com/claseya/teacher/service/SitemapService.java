package com.claseya.teacher.service;

import com.claseya.academic.dto.SubjectResponse;
import com.claseya.academic.service.SubjectService;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherProfile;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.specification.TeacherSpecifications;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SEO-001: public sitemaps. The teacher URLs come from the canonical D1 specification
 * ({@link TeacherSpecifications#visible()}), so a hidden profile can never leak into a sitemap.
 *
 * <p>This service only reads public identifiers. It never loads emails, addresses or coordinates.
 */
@Service
public class SitemapService {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String URLSET_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final int MAX_SUBJECT_URLS = 50;
    private static final int DEFAULT_CHUNK_SIZE = 10_000;

    private final TeacherProfileRepository teacherProfileRepository;
    private final SubjectService subjectService;
    private final String publicUrl;
    private final int chunkSize;

    public SitemapService(TeacherProfileRepository teacherProfileRepository,
                          SubjectService subjectService,
                          @Value("${app.public-url:http://localhost:5173}") String publicUrl,
                          @Value("${app.sitemap.chunk-size:" + DEFAULT_CHUNK_SIZE + "}") int chunkSize) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.subjectService = subjectService;
        this.publicUrl = stripTrailingSlash(publicUrl);
        this.chunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
    }

    /** {@code /sitemap.xml}: an index for the static routes plus one chunk per 10k teachers. */
    @Transactional(readOnly = true)
    public String index() {
        long visibleTeachers = teacherProfileRepository.count(TeacherSpecifications.visible());
        int chunks = (int) Math.ceil((double) visibleTeachers / chunkSize);
        String lastmod = latestTeacherUpdate();

        StringBuilder xml = new StringBuilder(XML_HEADER);
        xml.append("<sitemapindex xmlns=\"").append(URLSET_NS).append("\">");
        appendSitemap(xml, publicUrl + "/sitemaps/static.xml", null);
        for (int page = 1; page <= chunks; page++) {
            appendSitemap(xml, publicUrl + "/sitemaps/teachers-" + page + ".xml", lastmod);
        }
        xml.append("</sitemapindex>");
        return xml.toString();
    }

    /** {@code /sitemaps/static.xml}: landing, search and the most used subject searches. */
    @Transactional(readOnly = true)
    public String staticSitemap() {
        List<UrlEntry> entries = new ArrayList<>();
        entries.add(new UrlEntry(publicUrl + "/", null, "daily", "1.0"));
        entries.add(new UrlEntry(publicUrl + "/buscar", null, "weekly", "0.8"));

        for (SubjectResponse subject : subjectService.popular(MAX_SUBJECT_URLS).content()) {
            entries.add(new UrlEntry(subjectSearchUrl(subject.name()), null, "weekly", "0.6"));
        }
        return urlset(entries);
    }

    /** {@code /sitemaps/teachers-{page}.xml}: one chunk of visible teachers, newest first. */
    @Transactional(readOnly = true)
    public String teachers(int page) {
        long visibleTeachers = teacherProfileRepository.count(TeacherSpecifications.visible());
        int chunks = (int) Math.ceil((double) visibleTeachers / chunkSize);
        if (page < 1 || page > chunks) {
            // SEO-001 RF-13: no empty invented chunk. The global handler turns this into a 404.
            throw new ResourceNotFoundException();
        }

        Pageable pageable = PageRequest.of(page - 1, chunkSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
        List<TeacherProfile> profiles =
                teacherProfileRepository.findAll(TeacherSpecifications.visible(), pageable).getContent();

        List<UrlEntry> entries = new ArrayList<>(profiles.size());
        for (TeacherProfile profile : profiles) {
            entries.add(new UrlEntry(teacherUrl(profile.getId().toString()),
                    profile.getUpdatedAt() == null ? null : profile.getUpdatedAt().toString(),
                    "weekly", "0.7"));
        }
        return urlset(entries);
    }

    private String latestTeacherUpdate() {
        return teacherProfileRepository
                .findAll(TeacherSpecifications.visible(),
                        PageRequest.of(0, 1, Sort.by(Sort.Order.desc("updatedAt"))))
                .stream()
                .findFirst()
                .map(TeacherProfile::getUpdatedAt)
                .map(Instant::toString)
                .orElse(null);
    }

    private String teacherUrl(String teacherId) {
        return publicUrl + "/profesores/" + teacherId;
    }

    private String subjectSearchUrl(String subjectName) {
        return UriComponentsBuilder.fromUriString(publicUrl)
                .path("/buscar")
                .queryParam("materia", subjectName)
                .build()
                .encode()
                .toUriString();
    }

    private String urlset(List<UrlEntry> entries) {
        StringBuilder xml = new StringBuilder(XML_HEADER);
        xml.append("<urlset xmlns=\"").append(URLSET_NS).append("\">");
        for (UrlEntry entry : entries) {
            xml.append("<url><loc>").append(escapeXml(entry.location())).append("</loc>");
            if (entry.lastmod() != null) {
                xml.append("<lastmod>").append(escapeXml(entry.lastmod())).append("</lastmod>");
            }
            if (entry.changeFrequency() != null) {
                xml.append("<changefreq>").append(entry.changeFrequency()).append("</changefreq>");
            }
            if (entry.priority() != null) {
                xml.append("<priority>").append(entry.priority()).append("</priority>");
            }
            xml.append("</url>");
        }
        xml.append("</urlset>");
        return xml.toString();
    }

    private void appendSitemap(StringBuilder xml, String location, String lastmod) {
        xml.append("<sitemap><loc>").append(escapeXml(location)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(escapeXml(lastmod)).append("</lastmod>");
        }
        xml.append("</sitemap>");
    }

    /** XML 1.0 escaping for the values we put in the document (URLs, instants). */
    static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:5173";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private record UrlEntry(String location, String lastmod, String changeFrequency, String priority) {
    }
}
