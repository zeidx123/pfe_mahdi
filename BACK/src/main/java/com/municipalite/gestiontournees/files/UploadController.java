package com.municipalite.gestiontournees.files;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Authenticated replacement for the former public static upload directory. */
@RestController
@RequiredArgsConstructor
public class UploadController {
    private final AccessService access;
    private final AiProperties properties;
    private final TourneeRepository tours;
    private final ImageAnalysisRepository analyses;

    @GetMapping("/uploads/{*path}")
    public ResponseEntity<Resource> read(@PathVariable String path) throws IOException {
        String requested = path.startsWith("/") ? path.substring(1) : path;
        if (requested.contains("\\") || requested.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chemin invalide");
        }
        // Legacy tour records store only the photo filename.
        String relative = requested.contains("/") ? requested : "photos/" + requested;
        String[] parts = relative.split("/", -1);
        if (parts.length != 2 || parts[1].isBlank()) throw missing();
        String directory = switch (parts[0]) {
            case "photos" -> properties.getStorage().getPhotosDir();
            case "cv" -> properties.getStorage().getCvDir();
            case "documents" -> properties.getStorage().getDocumentsDir();
            default -> throw missing();
        };
        String url = "/uploads/" + relative;
        var user = access.currentUser();
        if (!access.isSupervisor() && (!parts[0].equals("photos") || !driverMayRead(url, user.getId(), user.getPhoto()))) {
            throw new AccessDeniedException("Fichier non autorisé");
        }
        Path root = Path.of(directory).toAbsolutePath().normalize();
        Path target = root.resolve(parts[1]).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) throw missing();
        if (!target.toRealPath().startsWith(root.toRealPath())) throw missing();
        String mime = switch (extension(parts[1])) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .builder(mime.startsWith("image/") ? "inline" : "attachment")
                        .filename(parts[1]).build().toString())
                .body(new FileSystemResource(target));
    }

    private boolean driverMayRead(String url, String userId, String profilePhoto) {
        if (samePhoto(url, profilePhoto)) return true;
        return tours.findAll().stream().filter(t -> Objects.equals(userId, t.getChauffeurId())).anyMatch(t ->
                samePhoto(url, t.getPhotoFin())
                || (t.getPhotosPoints() != null && t.getPhotosPoints().values().stream().anyMatch(p -> samePhoto(url, p)))
                || (t.getVisits() != null && t.getVisits().stream().anyMatch(v -> samePhoto(url, v.getBeforePhoto()) || samePhoto(url, v.getAfterPhoto()) || samePhoto(url, v.getLegacyPhoto())))
                || analyses.findByTourneeIdOrderByCreatedAtDesc(t.getId()).stream().anyMatch(a ->
                    samePhoto(url, a.getPhotoUrl()) || samePhoto(url, a.getAfterPhotoUrl()) || samePhoto(url, a.getBeforePhotoUrl())));
    }

    private boolean samePhoto(String url, String stored) {
        if (stored == null || stored.isBlank()) return false;
        String normalized = stored.startsWith("/") ? stored : stored.contains("/") ? "/" + stored : "/uploads/photos/" + stored;
        return url.equals(normalized);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
    }
}
