package com.projectpilot.utils

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object Mailer {

    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", Config.smtpHost)
            put("mail.smtp.port", Config.smtpPort)
        }

        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(Config.fromEmail, Config.appPassword)
            }
        })
    }

    fun sendVerificationCode(to: String, code: String, subject: String, content: String): Boolean {
        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(Config.fromEmail, "ProjectPilot"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)
                setText(content)
            }

            Transport.send(message)
            logger.info { "Verification email sent to $to" }
            true
        } catch (e: MessagingException) {
            logger.error(e) { "Failed to send verification email to $to" }
            false
        }
    }
}
