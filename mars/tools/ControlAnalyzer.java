package mars.tools;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Observable;

import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.TitledBorder;

import mars.*;
import mars.ProgramStatement;
import mars.mips.hardware.AccessNotice;
import mars.mips.hardware.AddressErrorException;
import mars.mips.hardware.Memory;
import mars.mips.hardware.MemoryAccessNotice;
import mars.mips.instructions.BasicInstruction;
import mars.mips.instructions.BasicInstructionFormat;

/**
 * A MARS Tool for analyzing the control unit of a MIPS processor.
 * @author Piyush Singh
 */

public class ControlAnalyzer extends AbstractMarsToolAndApplication {

    private static final String name = "Control Unit Analyzer";
    private static final String version = "Version 1.0";
    private static final String heading = "";

    /**
     * Class Constructor
     */
    public ControlAnalyzer(){
        super(ControlAnalyzer.name + ", " + ControlAnalyzer.version, ControlAnalyzer.heading);
    }

    public String getName() {
        return name;
    }
    /**
     * Utility Variables for 
     */
    private static final int MAX = 9;
    private static final int BRANCH = 0;
    private static final int JUMP = 1;
    private static final int ALUSRC = 2;
    private static final int ALUOP = 3;
    private static final int MEMREAD = 4;
    private static final int MEMWRITE = 5;
    private static final int REGWRITE = 6;
    private static final int REGDST = 7;
    private static final int MEMTOREG = 8;

    /**
     * Utility Variables for 
     */
    private static final int LO = 0;    //Low 
    private static final int HI = 1;    //High
    private static final int DC = -1;    //Don't Care

    
    /**
     * An array of Strings having all the control lines of the control unit.
     */
    private final String controlLines[] = {
        "Branch",
        "Jump",
        "ALUSrc",
        "ALUOp",
        "MemRead",
        "MemWrite",
        "RegWrite",
        "RegDst",
        "MemToReg"
    };

    /**
     * A Text Field for each of the control signal types.
     */
    private JTextField signalType[];

    /**
     * A counter for each of the control signal.
     */
    private int controlCounter[] = new int[MAX];   //Counter for each of the categories.
    
    /**
     * A progress Bar for each of the 
     */
    private JProgressBar controlStatsProgressBar[];

    private int signalStatus[] = new int[MAX];

    private String sourceLine;
    private int lineNumber;

    /**
     * A check if the log enable checkbox is checked. Marked as true, if the box is checked.
     */
    private static boolean logEnableCheck = false;

    private JTextArea logText;
    private JScrollPane logScroll;

    private JCheckBox logEnable;

    private JTextArea instruction;

    protected int lastAddr = -1;    //Last address. Counter against an infinite loop

    private int totalCount = 0;
    

    protected JComponent buildInstructionArea(){
        JPanel instructionPanel = new JPanel(new GridLayout());
        
        TitledBorder instructionTitle = new TitledBorder("Instruction");
        instructionTitle.setTitleJustification(TitledBorder.CENTER);
        instructionPanel.setBorder(instructionTitle);
        
        instruction = new JTextArea();
        Font font = new Font(Font.MONOSPACED, Font.BOLD, 14);
        instruction.setFont(font);
        instruction.setToolTipText("Displays the current instruction under consideration.");
        instructionPanel.add(instruction);
        
        return instructionPanel;
    }
    protected JComponent buildControlArea(){
        JPanel controlPanel = new JPanel(new GridLayout(5,1,0,0));
        
        TitledBorder title = new TitledBorder("Control Signals");
        title.setTitleJustification((TitledBorder.CENTER));
        controlPanel.setBorder(title);
        
        signalType = new JTextField[MAX];
        for(int i = 0;i<ControlAnalyzer.MAX;i++){
            signalType[i] = new JTextField("LOW", 5);
            signalType[i].setEditable(false);
        }
        
        for(int i = 0;i<MAX;i++){
            controlPanel.add(new JLabel(" " + controlLines[i] + ": "));
            controlPanel.add(signalType[i]);
        }
        
        return controlPanel;
    }
    private void logReset(){
        logText.setText("");
    }
    protected JComponent buildLogArea(){
        JPanel logPanel = new JPanel();
        
        TitledBorder logTitle = new TitledBorder("Runtime Log");
        logTitle.setTitleJustification(TitledBorder.CENTER);
        logPanel.setBorder(logTitle);
        
        logEnable = new JCheckBox("Enabled",logEnableCheck);
        logEnable.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent event){
                    logEnableCheck = event.getStateChange() == ItemEvent.SELECTED;
                    logReset();
                    logText.setEnabled(logEnableCheck);
                    if(logEnableCheck == true){
                        logText.setBackground(Color.WHITE);
                    } else {
                        logPanel.setBackground(getBackground());
                    }
                }
            }
        );
        logPanel.add(logEnable);
        
        logText = new JTextArea(5, 80);
        logText.setEnabled(logEnableCheck);
        if(logEnableCheck){
            // Font font = new Font(Font.MONOSPACED, Font.BOLD, 14);
            logText.setBackground(Color.WHITE);
            // logText.setFont(font);
        } else {
            logPanel.setBackground(getBackground());;
        }
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 10);
        logText.setFont(font);
        logText.setForeground(Color.BLACK);
        logText.setToolTipText("Displays the control unit activity log, if enabled");
        logScroll = new JScrollPane(logText, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        logPanel.add(logScroll);
        return logPanel;
    }
    protected JComponent buildBarArea(){
        JPanel barPanel = new JPanel(new GridLayout(10,1,0,0));
        TitledBorder barTitle = new TitledBorder("Statistics");
        barTitle.setTitleJustification(TitledBorder.CENTER);
        barPanel.setBorder(barTitle);
        controlStatsProgressBar = new JProgressBar[MAX];
        for(int i = 0;i<MAX;i++){
            controlStatsProgressBar[i] = new JProgressBar(JProgressBar.HORIZONTAL);
            controlStatsProgressBar[i].setStringPainted(false);
        }
        for(int i = 0;i<MAX;i++){
            barPanel.add(new JLabel(controlLines[i] + ": "));
            barPanel.add(controlStatsProgressBar[i]);
        }
        return barPanel;
    }
    protected JComponent buildMainDisplayArea(){
        Box mainPanel = Box.createVerticalBox();
        mainPanel.add(buildInstructionArea());
        mainPanel.add(buildControlArea());
        mainPanel.add(buildLogArea());
        mainPanel.add(buildBarArea());
        return mainPanel;
    }

    protected void addAsObserver(){
        addAsObserver(Memory.textBaseAddress, Memory.textLimitAddress);
    }

        String lw = "lw";
        String lh = "lh";
        String lb = "lb";
        String lhu = "lhu";
        String lbu = "lbu";
        
        String sw = "sw";
        String sh = "sh";
        String sb = "sb";

        String syscall = "syscall";
        String nop = "nop";
        
        String jr = "jr";
        String jalr = "jalr";

    protected void processMIPSUpdate(Observable resource, AccessNotice notice){
        if(!notice.accessIsFromMIPS()){
            return;
        }
        if(notice.getAccessType() != AccessNotice.READ){
            return;
        }
        MemoryAccessNotice m = (MemoryAccessNotice) notice;
        int a = m.getAddress();
        if(a == lastAddr){
            return ;
        }
        lastAddr = a;
        try{
            ProgramStatement statement = Memory.getInstance().getStatement(a);
            if(statement == null){
                return;
            }
            sourceLine = statement.getSource();
            lineNumber = statement.getSourceLine();
            String mnem =  statement.getInstruction().getName();
            BasicInstruction instruct = (BasicInstruction) statement.getInstruction();
            BasicInstructionFormat format = instruct.getInstructionFormat();
            if(format == BasicInstructionFormat.R_FORMAT && mnem.compareTo(nop) != 0 && mnem.compareTo(syscall) != 0 && mnem.compareTo(jr) != 0 && mnem.compareTo(jalr) != 0){
                signalStatus[ControlAnalyzer.REGDST]   = 1;
                signalStatus[ControlAnalyzer.ALUSRC]   = 0;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 1;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 1;
            } else if(format == BasicInstructionFormat.I_BRANCH_FORMAT){
                signalStatus[ControlAnalyzer.REGDST]   = -1;
                signalStatus[ControlAnalyzer.ALUSRC]   = 0;
                signalStatus[ControlAnalyzer.MEMTOREG] = -1;
                signalStatus[ControlAnalyzer.REGWRITE] = 0;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 1;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
            } else if(format == BasicInstructionFormat.J_FORMAT){
                signalStatus[ControlAnalyzer.REGDST]   = 0;
                signalStatus[ControlAnalyzer.ALUSRC]   = 0;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 0;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 1;
            } else if(mnem.compareTo(lw) == 0 || mnem.compareTo(lh) == 0 || mnem.compareTo(lb) == 0 || mnem.compareTo(lhu) == 0 || mnem.compareTo(lbu) == 0){
                signalStatus[ControlAnalyzer.REGDST]   = 0;
                signalStatus[ControlAnalyzer.ALUSRC]   = 1;
                signalStatus[ControlAnalyzer.MEMTOREG] = 1;
                signalStatus[ControlAnalyzer.REGWRITE] = 1;
                signalStatus[ControlAnalyzer.MEMREAD]  = 1;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
            } else if(mnem.compareTo(sw) == 0 || mnem.compareTo(sh) == 0 || mnem.compareTo(sb) == 0) {
                signalStatus[ControlAnalyzer.REGDST]   = -1;
                signalStatus[ControlAnalyzer.ALUSRC]   = 1;
                signalStatus[ControlAnalyzer.MEMTOREG] = -1;
                signalStatus[ControlAnalyzer.REGWRITE] = 0;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 1;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
            } else if(mnem.compareTo(syscall) == 0 || mnem.compareTo(nop) == 0) {
                signalStatus[ControlAnalyzer.REGDST]   = 0;
                signalStatus[ControlAnalyzer.ALUSRC]   = 0;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 0;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
            } else if(mnem.compareTo(jr) == 0){
                signalStatus[ControlAnalyzer.REGDST]   = -1;
                signalStatus[ControlAnalyzer.ALUSRC]   = -1;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 0;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 1;
            } else if(mnem.compareTo(jalr) == 0){
                signalStatus[ControlAnalyzer.REGDST]   = -1;
                signalStatus[ControlAnalyzer.ALUSRC]   = -1;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 1;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 1;
            } else if(format == BasicInstructionFormat.I_FORMAT) {
                signalStatus[ControlAnalyzer.REGDST]   = 0;
                signalStatus[ControlAnalyzer.ALUSRC]   = 1;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 1;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
            } else {
                signalStatus[ControlAnalyzer.REGDST]   = 0;
                signalStatus[ControlAnalyzer.ALUSRC]   = 0;
                signalStatus[ControlAnalyzer.MEMTOREG] = 0;
                signalStatus[ControlAnalyzer.REGWRITE] = 0;
                signalStatus[ControlAnalyzer.MEMREAD]  = 0;
                signalStatus[ControlAnalyzer.MEMWRITE] = 0;
                signalStatus[ControlAnalyzer.BRANCH]   = 0;
                signalStatus[ControlAnalyzer.ALUOP]    = 0;
                signalStatus[ControlAnalyzer.JUMP]     = 0;
            }

            for(int i = 0;i<MAX;i++){
                if(signalStatus[i] == 1){
                    controlCounter[i]++;
                }
            }
        } catch(AddressErrorException err){
            err.printStackTrace();
        }
        // updateDisplay();
    }

    protected void printInstructionLine(){
        String lineStr = String.valueOf(lineNumber);
        if(sourceLine != null) {
            sourceLine = sourceLine.trim();
        }
        String str = lineStr + ": " + sourceLine;
        instruction.setText(str);
    }
    protected void setSignalStatus(){
        for(int i = 0;i<MAX;i++){
            if(signalStatus[i] == 0){
                signalType[i].setText("LOW");
                signalType[i].setForeground(Color.RED);
            } else if(signalStatus[i] == 1){
                signalType[i].setText("HIGH");
                signalType[i].setForeground(Color.GREEN);
            } else {
                signalType[i].setText("Don't Care");
                signalType[i].setForeground(Color.BLACK);
            }
        }
    }
    int cnt = 0;
    protected void writeToLog(){
        // if(logEnableCheck){
        //     String t = "[Line: " + cnt + "] :: ";
        //     for(int i = 0;i<MAX;i++){
        //         t += controlLines[i] + ": " + String.valueOf(controlCounter[i]) + " ";
        //     }
        //     t += "\n";
        //     logText.append(t);
        //     logText.setCaretPosition(logText.getDocument().getLength());
        // }
    }
    protected void setProgressBar(){
        totalCount = 0;
        for(int i = 0;i<MAX;i++){
            totalCount += controlCounter[i];
        }
        for(int i = 0;i<MAX;i++){
            String count = String.valueOf(controlCounter[i]);
            controlStatsProgressBar[i].setStringPainted(true);
            controlStatsProgressBar[i].setString(count);
            controlStatsProgressBar[i].setMaximum(totalCount);
            controlStatsProgressBar[i].setValue(controlCounter[i]);
        }
    }
    
    // @Override
    protected void updateDisplay() {
        cnt++;
        printInstructionLine();
        setSignalStatus();
        // writeToLog();
        // System.out.println(cnt);
        if(logEnableCheck && cnt%2 == 0){
            char val;
            String t = "[Line: " + lineNumber + "] :: ";
            for(int i = 0;i<MAX;i++){
                if(signalStatus[i] == 1){
                    val = '1';
                } else if(signalStatus[i] == 0) {
                    val = '0';
                } else {
                    val = 'x';
                }
                t += controlLines[i] + ": " + String.valueOf(val + " ");
            }
            t += "\n";
            logText.append(t);
            logText.setCaretPosition(logText.getDocument().getLength());
        }
        setProgressBar();        
    }

    // @Override
    protected void reset(){
        lineNumber = 0;
        lastAddr = -1;
        sourceLine = "";
        
        logEnable.setSelected(false);
        logEnableCheck = false;

        totalCount = 0;
        for(int i = 0;i<MAX;i++){
            controlCounter[i] = 0;
        }

        signalStatus[ControlAnalyzer.REGDST]   = 0;
        signalStatus[ControlAnalyzer.ALUSRC]   = 0;
        signalStatus[ControlAnalyzer.MEMTOREG] = 0;
        signalStatus[ControlAnalyzer.REGWRITE] = 0;
        signalStatus[ControlAnalyzer.MEMREAD]  = 0;
        signalStatus[ControlAnalyzer.MEMWRITE] = 0;
        signalStatus[ControlAnalyzer.BRANCH]   = 0;
        signalStatus[ControlAnalyzer.ALUOP]    = 0;
        signalStatus[ControlAnalyzer.JUMP]     = 0;

        updateDisplay();
    }

}