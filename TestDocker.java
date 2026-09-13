import org.testcontainers.DockerClientFactory;
public class TestDocker {
    public static void main(String[] args) {
        System.out.println("Docker client valid: " + DockerClientFactory.instance().isDockerAvailable());
    }
}
