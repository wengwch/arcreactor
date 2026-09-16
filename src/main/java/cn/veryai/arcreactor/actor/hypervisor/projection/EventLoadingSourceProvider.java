package cn.veryai.arcreactor.actor.hypervisor.projection;

import org.apache.pekko.NotUsed;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.persistence.query.typed.javadsl.LoadEventQuery;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Loads backtracking event payloads before a synchronous JDBC handler receives them. */
final class EventLoadingSourceProvider<Event> extends SourceProvider<Offset, EventEnvelope<Event>> {
    private final SourceProvider<Offset, EventEnvelope<Event>> delegate;
    private final LoadEventQuery loadEventQuery;

    EventLoadingSourceProvider(SourceProvider<Offset, EventEnvelope<Event>> delegate) {
        if (!(delegate instanceof LoadEventQuery query)) {
            throw new IllegalArgumentException("source provider must support LoadEventQuery");
        }
        this.delegate = delegate;
        this.loadEventQuery = query;
    }

    @Override
    public CompletionStage<Source<EventEnvelope<Event>, NotUsed>> source(
            Supplier<CompletionStage<Optional<Offset>>> offset) {
        return delegate.source(offset).thenApply(source -> source.mapAsync(1, this::loadEvent));
    }

    private CompletionStage<EventEnvelope<Event>> loadEvent(EventEnvelope<Event> envelope) {
        if (envelope.getOptionalEvent().isPresent()) {
            return CompletableFuture.completedFuture(envelope);
        }
        return loadEventQuery.<Event>loadEnvelope(envelope.persistenceId(), envelope.sequenceNr())
                .thenApply(loaded -> {
                    // Fail the stream if loading did not return a payload, rather than advancing the offset.
                    loaded.event();
                    // The loaded envelope has a lookup offset, not the original slice query's progress.
                    return new EventEnvelope<>(envelope.offset(), envelope.persistenceId(),
                            envelope.sequenceNr(), loaded.eventOption(), envelope.timestamp(),
                            loaded.eventMetadata(), envelope.entityType(), envelope.slice());
                });
    }

    @Override
    public Offset extractOffset(EventEnvelope<Event> envelope) {
        return delegate.extractOffset(envelope);
    }

    @Override
    public long extractCreationTime(EventEnvelope<Event> envelope) {
        return delegate.extractCreationTime(envelope);
    }
}
