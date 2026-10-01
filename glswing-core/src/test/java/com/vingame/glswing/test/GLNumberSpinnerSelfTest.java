package com.vingame.glswing.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLNumberSpinner;

/** CPU-only checks for numeric spinner constraints, editing and history. */
public final class GLNumberSpinnerSelfTest {
	private GLNumberSpinnerSelfTest() { }

	public static void main(String[] args) {
		testRangeAndIntegerValues();
		testEditorAndButtons();
		testHistory();
        testReset();
        testGroup();
		System.out.println("GL number spinner self-test passed.");
	}

	private static void testRangeAndIntegerValues() {
		GLNumberSpinner spinner = new GLNumberSpinner(0, 12, 4.6);
		AtomicInteger changes = new AtomicInteger();
		spinner.addChangeListener(event -> changes.incrementAndGet());
		spinner.setIntegerValuesOnly(true);
		check(spinner.getValue() == 5 && changes.get() == 1,
				"Integer mode did not normalize and notify");
		spinner.setValue(20);
		check(spinner.getValue() == 12
				&& !spinner.getIncrementButton().isEnabled()
				&& spinner.getDecrementButton().isEnabled(),
				"Bounded spinner did not clamp its value");
		spinner.setRange(-3, 3);
		check(spinner.getValue() == 3,
				"Range change did not clamp the current value");
	}

	private static void testEditorAndButtons() {
		GLNumberSpinner spinner = new GLNumberSpinner(0, 12, 3);
		spinner.setIntegerValuesOnly(true);
		check(!spinner.getEditor().getInputFilter().test("2.5")
				&& spinner.getEditor().getInputFilter().test("12"),
				"Integer spinner accepted decimal editor input");
		spinner.getEditor().setText("8");
		check(spinner.getEditor().commit() && spinner.getValue() == 8,
				"Committed editor text did not update the spinner");
		spinner.getIncrementButton().doClick();
		spinner.getDecrementButton().doClick();
		check(spinner.getValue() == 8,
				"Spinner buttons did not change by one step");
		spinner.setStep(2);
		spinner.getIncrementButton().doClick();
		check(spinner.getValue() == 10,
				"Spinner button ignored the configured step");
	}

	private static void testHistory() {
		GLNumberSpinner spinner = new GLNumberSpinner(0, 10, 4);
		GLHistoryManager history = new GLHistoryManager();
		spinner.setGLHistoryManager(history);
		spinner.getIncrementButton().doClick();
		check(spinner.getValue() == 5 && history.canUndo(),
				"Button change was not recorded");
		history.undo();
		check(spinner.getValue() == 4,
				"Undo did not restore the previous spinner value");
		history.redo();
		check(spinner.getValue() == 5,
				"Redo did not restore the changed spinner value");
	}

    private static void testGroup(){
        var first=new GLNumberSpinner(1,1000,20);var second=new GLNumberSpinner(1,500,40);
        var group=new com.vingame.glswing.ui.GLNumberSpinnerGroup(first,second);
        group.setBounds(0,0,160,56);group.layoutGL();
        var history=new GLHistoryManager();group.setGLHistoryManager(history);
        selectGroup(first);
        first.getEditor().setText("120");
        check(second.getEditor().getText().equals("120"),"Group did not mirror text");
        check(first.getEditor().commit(),"Group commit rejected");
        check(first.getValue()==120&&second.getValue()==120,"Group commit did not update both values");
        history.undo();check(first.getValue()==20&&second.getValue()==40,"Group undo not atomic");
        history.redo();check(first.getValue()==120&&second.getValue()==120,"Group redo not atomic");
        selectGroup(first);first.getEditor().setText("600");
        check(!first.getEditor().commit(),"Group accepted value outside second member range");
        check(first.getValue()==120&&second.getValue()==120,"Invalid group edit changed values");
        selectGroup(first);first.getEditor().setText("30");first.getEditor().cancel();
        check(first.getValue()==120&&second.getValue()==120&&second.getEditor().getText().equals("120"),"Group cancel did not restore editors");
    }
    private static void selectGroup(GLNumberSpinner spinner){
        var editor=spinner.getEditor();
        editor.mousePressed(mouse(editor,java.awt.event.MouseEvent.MOUSE_PRESSED,10));
        editor.mouseDragged(mouse(editor,java.awt.event.MouseEvent.MOUSE_DRAGGED,42));
        editor.mouseReleased(mouse(editor,java.awt.event.MouseEvent.MOUSE_RELEASED,42));
    }
    private static com.vingame.glswing.input.GLMouseEvent mouse(com.vingame.glswing.ui.GLTextEditor editor,int id,float y){
        var original=new java.awt.event.MouseEvent(new javax.swing.JPanel(),id,System.currentTimeMillis(),0,10,(int)y,1,false,java.awt.event.MouseEvent.BUTTON1);
        var pick=new com.vingame.glswing.input.GLPickResult(0,editor,10,y,10,y,1,0,0,1,0,0);
        return new com.vingame.glswing.input.GLMouseEvent(original,pick,0,32);
    }
    private static void testReset(){
        var spinner=new GLNumberSpinner(0,100,20);
        var history=new GLHistoryManager();spinner.setGLHistoryManager(history);
        spinner.setSize(180,28);spinner.setUnit("px");
        check(!spinner.getEditor().getText().contains("px"),"Unit leaked into document");
        spinner.setValue(40);spinner.getResetMenuItem().doClick();
        check(spinner.getValue()==20,"Reset did not restore constructor value");
        history.undo();check(spinner.getValue()==40,"Reset is not undoable");
        spinner.setGLLocalizer(com.vingame.glswing.ui.GLLocalizer.GERMAN);
        check(spinner.getResetMenuItem().getText().equals("Zurücksetzen"),"Reset translation did not update");
        spinner.setDefaultValue(5);spinner.reset();check(spinner.getValue()==5,"Default reset override ignored");
        var other=new GLNumberSpinner(1,100,10);other.setValue(30);
        spinner.setResetAction(()->{spinner.setValue(5);other.reset();});spinner.getResetMenuItem().doClick();
        check(other.getValue()==10,"Compound reset did not run");
    }
    private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
