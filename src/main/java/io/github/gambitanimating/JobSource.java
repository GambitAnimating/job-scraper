package io.github.gambitanimating;

import java.io.IOException;
import java.util.List;

public interface JobSource {
    List<Job> fetch() throws IOException, InterruptedException;
}
