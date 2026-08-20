package com.rgapro1.ocaso.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.rgapro1.ocaso.data.local.ClientDao;
import com.rgapro1.ocaso.data.local.ClientEntity;
import com.rgapro1.ocaso.data.local.DatabaseProvider;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Room repository. Database work is kept off the UI thread and callbacks return on main. */
public final class ClientRepositoryImpl implements AutoCloseable {
    private final ClientDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ClientRepositoryImpl(Context context) {
        dao = DatabaseProvider.get(context).clientDao();
    }

    public void upsert(ClientEntity client) {
        if (client == null) return;
        io.execute(() -> {
            String identity = normalizeIdentity(client.identityNumber);
            if (!identity.isEmpty()) {
                ClientEntity existing = dao.findByIdentity(identity);
                if (existing != null && !existing.clientId.equals(client.clientId)) {
                    client.clientId = existing.clientId;
                    client.createdAt = existing.createdAt;
                }
                client.identityNumber = identity;
            }
            if (client.createdAt <= 0) client.createdAt = System.currentTimeMillis();
            client.updatedAt = System.currentTimeMillis();
            dao.upsert(client);
        });
    }

    public void search(String query, String identity, ResultCallback callback) {
        if (callback == null) return;
        final String safeQuery = query == null ? "" : query.trim();
        final String safeIdentity = normalizeIdentity(identity);
        io.execute(() -> {
            try {
                List<ClientEntity> result = dao.search(safeQuery, safeIdentity);
                main.post(() -> callback.onResult(result));
            } catch (RuntimeException e) {
                main.post(() -> callback.onError(e));
            }
        });
    }

    private static String normalizeIdentity(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }

    @Override public void close() {
        io.shutdownNow();
    }

    public interface ResultCallback {
        void onResult(List<ClientEntity> result);
        default void onError(Exception error) {}
    }
}
