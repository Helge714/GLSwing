package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import com.vingame.glswing.history.GLHistoryManager;

/**
 * Groups value edits independently of widget placement. Values must be immutable
 * snapshots. A begin/update/commit sequence produces one history operation.
 * Use on the UI event thread, as with the controls it binds.
 *
 * @param <T> immutable value snapshot shared by the members
 */
public final class GLInputGroup<T> {
	private record Member<T>(Supplier<T> read,Consumer<T> write) { }
	private final List<Member<T>> members=new ArrayList<>();
	private final Supplier<GLHistoryManager> history;
	private List<T> before;
	private String name;
	/** Creates a group that resolves its history manager when an edit is committed. */
	public GLInputGroup(Supplier<GLHistoryManager> history) { this.history=Objects.requireNonNull(history); }
	/** Adds one readable and writable value endpoint to the group. */
	public void add(Supplier<T> read,Consumer<T> write) {
		if(before!=null) throw new IllegalStateException("Cannot change members during an edit");
		members.add(new Member<>(Objects.requireNonNull(read),Objects.requireNonNull(write)));
	}
	/** Captures every member's baseline and starts one named edit. */
	public void begin(String name) {
		if(before!=null) throw new IllegalStateException("An input-group edit is already active");
		this.name=Objects.requireNonNull(name); before=snapshot();
	}
	/** Applies an operation to every member. An edit must already be active. */
	public void update(UnaryOperator<T> operation) {
		if(before==null) throw new IllegalStateException("No active edit");
		for(Member<T> member:members) member.write.accept(operation.apply(member.read.get()));
	}
	/** Commits all changes since {@link #begin(String)} as one history entry. */
	public void commit() {
		if(before==null) return;
		List<T> start=before,end=snapshot(); before=null;
		List<Member<T>> targets=List.copyOf(members);
		GLHistoryManager manager=history.get();
		if(!start.equals(end) && manager!=null) manager.record(name,
				()->restore(targets,start),()->restore(targets,end));
	}
	/** Restores the captured baseline without adding a history entry. */
	public void cancel() { if(before!=null) {List<T> values=before;before=null;restore(members,values);} }
	/** Returns whether a begin/update/commit sequence is active. */
	public boolean isEditing() { return before!=null; }
	private List<T> snapshot() { return members.stream().map(m->m.read.get()).toList(); }
	private static <T> void restore(List<Member<T>> targets,List<T> values) {
		for(int i=0;i<targets.size();i++) targets.get(i).write.accept(values.get(i));
	}
}
