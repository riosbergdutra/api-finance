package com.api.finance.user.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User { /** Identificador interno — gerado pelo banco. Usado em todas as FK. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Identificador do usuário no Keycloak (claim "sub" do JWT).
     * Único e imutável após criação.
     */
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private UUID keycloakId;

    /** Nome completo do usuário — sincronizado do JWT na criação. */
    @Column(name = "name", nullable = false)
    private String nome;

    /** E-mail do usuário — sincronizado do JWT na criação. Único no sistema. */
    @Column(nullable = false, unique = true)
    private String email;

    /** URL do avatar do usuário. Pode ser null se não configurado. */
    @Column(name = "avatar_url")
    private String urlAvatar;

    /**
     * Locale de exibição do usuário (ex: "pt-BR", "en-US").
     * Usado pelo frontend para formatação de datas e moedas.
     */
    @Column(nullable = false, length = 5)
    private String locale = "pt-BR";

    /** Indica se a conta está ativa. Contas inativas não podem fazer login. */
    @Column(name = "is_active", nullable = false)
    private boolean ativo = true;

    /** Data/hora de criação — imutável após inserção. */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;

    /** Data/hora da última atualização do perfil. */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime atualizadoEm;
}
