package cn.veryai.arcreactor.util;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.Locale;

public class SSHValidator {
    /** Checks the SSH handshake; authentication rejection still confirms connectivity. */
    public boolean validateSSH(String host, int port, String user, int timeoutMs) {
        if (host == null || host.isBlank() || user == null || user.isBlank()
                || port < 1 || port > 65535 || timeoutMs <= 0) {
            throw new IllegalArgumentException("SSH host, user, valid port and positive timeout are required");
        }
        Session session = null;
        try {
            session = new JSch().getSession(user, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            // Probe without credentials or interactive/GSSAPI authentication.
            session.setConfig("PreferredAuthentications", "none");
            session.setTimeout(timeoutMs);
            session.connect(timeoutMs);
            return true;
        } catch (JSchException e) {
            String message = e.getMessage();
            if (message == null) return false;
            String normalized = message.toLowerCase(Locale.ROOT);
            return normalized.contains("auth fail") || normalized.contains("userauth fail");
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
