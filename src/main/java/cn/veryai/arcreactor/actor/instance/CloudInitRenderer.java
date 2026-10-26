package cn.veryai.arcreactor.actor.instance;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Renders classpath templates without performing any workflow or OpenStack operations. */
@Component
public final class CloudInitRenderer {
  private final Configuration configuration;
  private final String manilaFsName;

  public CloudInitRenderer(Configuration configuration,
      @Value("${openstack.manila-fs-name:}") String manilaFsName) {
    this.configuration = configuration;
    this.manilaFsName = manilaFsName;
  }

  public void validateConfiguration() {
    if (manilaFsName == null || !manilaFsName.matches("[A-Za-z0-9_.-]+")) {
      throw new RenderingException("openstack.manila-fs-name must be configured with a valid Ceph filesystem name", null);
    }
  }

  public String render(Map<String, Object> values) {
    validateConfiguration();
    Map<String, Object> model = new HashMap<>(values);
    model.put("manila_fs_name", manilaFsName);
    try (StringWriter userData = new StringWriter()) {
      Template template = configuration.getTemplate("cloud_init.yaml.ftl", StandardCharsets.UTF_8.name());
      template.process(model, userData);
      return userData.toString();
    } catch (IOException | TemplateException | RuntimeException error) {
      // Template errors can include secret values. Keep the public failure reason independent of the model.
      throw new RenderingException("Cloud-init template rendering failed", error);
    }
  }

  public static final class RenderingException extends RuntimeException {
    private RenderingException(String detail, Throwable cause) { super(detail, cause); }
  }
}
