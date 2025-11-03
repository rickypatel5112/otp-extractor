package com.otp_extractor.notification_service.services;

import com.otp_extractor.notification_service.dtos.ResetRequestResponse;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetConsumer {

    private final EmailSenderService emailSenderService;

    @RabbitListener(queues = "password-reset-queue")
    public void sendEmail(ResetRequestResponse resetRequestResponse) throws MessagingException {
        final String to = resetRequestResponse.getEmail();
        final String token = resetRequestResponse.getResetToken();
        final String frontEndUrl = resetRequestResponse.getFrontEndUrl();
        final String appName = "OTP Extractor"; // or inject via @Value("${app.name}")

        String body = """
                <!DOCTYPE html>
                <html>
                  <body style="font-family: Arial, sans-serif; color: #333; background-color: #f7f7f7; padding: 20px;">
                    <div style="max-width: 600px; margin: auto; background-color: white; padding: 30px; border-radius: 10px;">
                      <h2 style="color: #2a4d8f;">Password Reset Request</h2>
                      <p>Hello,</p>
                      <p>We received a request to reset the password for your account. If you made this request, please click the button below to reset your password:</p>
                      <p style="text-align: center;">
                        <a href="%s/reset-password?token=%s"
                           style="background-color: #2a4d8f; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; display: inline-block;">
                          Reset Password
                        </a>
                      </p>
                      <p>This link will expire in 10 minutes for your security.</p>
                      <p>If you did not request a password reset, you can safely ignore this email — your account will remain secure.</p>
                      <br>
                      <p>Best regards,<br>The %s Team</p>
                    </div>
                  </body>
                </html>
                """.formatted(frontEndUrl, token, appName);

        emailSenderService.sendEmail(to, body);
    }
}
