package lk.emergency.client.network;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.client.recovery.*;
import lk.emergency.common.*;

public interface Transport extends AutoCloseable {
  CompletableFuture<Envelope> send(Envelope request);

  CompletableFuture<Envelope> resend(Envelope request);

  boolean isClosed();

  default Envelope call(String type, Object payload) throws IOException, TimeoutException {
    Envelope request = Envelope.request(type, UUID.randomUUID().toString(), payload);
    try {
      return send(request).get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new TimeoutException(
          "Confirmation pending; original request ID: " + request.requestId());
    } catch (ExecutionException e) {
      throw new IOException(
          "Connection failed; login and reconcile uncertain actions", e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    }
  }

  @Override
  void close();
}
