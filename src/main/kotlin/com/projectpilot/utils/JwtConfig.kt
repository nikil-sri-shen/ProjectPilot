package com.projectpilot.utils

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.util.*
import javax.crypto.SecretKey

object JwtConfig {
    private val secretKey = Config.jwtSecret
    private val key : SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray())

    private const val validityInMs = 24 * 60 * 60 * 1000 // 1 day

    fun generateToken(email: String, username: String, role: String): String {
        val now = Date()
        val expiry = Date(now.time + validityInMs)

        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .claim("username", username) // Add custom claim
            .claim("role", role) // Default role, can be customized later
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun validateToken(token: String): Map<String, String>? {
        return try {
            val claims = Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            // Extract relevant data (excluding password)
            mapOf(
                "email" to claims.subject,
                "username" to (claims["username"] as? String ?: ""),
                "role" to (claims["role"] as? String ?: "") // Default to STUDENT if no role is provided
            )
        } catch (e: Exception) {
            null
        }
    }
}