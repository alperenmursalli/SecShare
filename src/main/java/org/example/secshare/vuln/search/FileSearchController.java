package org.example.secshare.vuln.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.example.secshare.vuln.VulnProperties;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * VULN modulu: dosya adina gore arama.
 *  - vuln.search.sqli=true  -> girdi dogrudan SQL'e birlestirilir (SQL injection)
 *  - vuln.search.sqli=false -> parametreli sorgu (guvenli)
 *
 * Sizma teknigi (UNION): name=xyz%' UNION SELECT email || ':' || password_hash FROM users --
 *
 * SADECE YETKILI PENTEST / EGITIM ORTAMI ICIN.
 */
@RestController
@RequestMapping("/api/files")
public class FileSearchController {

    @PersistenceContext
    private EntityManager entityManager;

    private final VulnProperties vuln;

    public FileSearchController(VulnProperties vuln) {
        this.vuln = vuln;
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<String> search(@RequestParam("name") String name) {
        boolean sqli = vuln.isEnabled() && vuln.getSearch().isSqli();

        Query query;
        if (sqli) {
            // VULN: kullanici girdisi dogrudan sorguya birlestiriliyor
            String sql = "SELECT original_filename FROM files " +
                    "WHERE deleted = false AND original_filename LIKE '%" + name + "%'";
            query = entityManager.createNativeQuery(sql);
        } else {
            // Guvenli: parametreli sorgu
            String sql = "SELECT original_filename FROM files " +
                    "WHERE deleted = false AND original_filename LIKE :pattern";
            query = entityManager.createNativeQuery(sql);
            query.setParameter("pattern", "%" + name + "%");
        }

        return (List<String>) query.getResultList();
    }
}
