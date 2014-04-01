package dataflow.model.mapper;

import java.util.ArrayList;
import java.util.List;

import profile.commons.configuration.Configuration;
import profile.commons.metrics.JvmUsage;
import profile.mapper.Input;
import profile.mapper.MapperBuffer;
import profile.mapper.MapperCounters;
import profile.mapper.Merge;
import profile.mapper.MergeInfo;
import profile.mapper.Spill;
import profile.mapper.SpillInfo;


public class Mapper {

    // e.g., attempt_201403211644_0002_m_000013_0
    private String taskId;
    private boolean isMapRunning;
    private String runningPhase;
    private Configuration conf;

    private InputSplit split;
    private MapFunc mapFunc;
    private SpillBuffer spillBuffer;
    private MemCombineFunc memCombineFunc;
    private List<SpillPiece> spills;
    private DiskCombineFunc diskCombineFunc;
    private List<Segment> mapOutputs;
    private List<MergeAction> merges;

    private long file_bytes_read;
    private long file_bytes_written;
    private long physical_memory_bytes;
    private long total_committed_bytes;

    public Mapper(Configuration conf) {
	this.conf = conf;
	split = new InputSplit();
	mapFunc = new MapFunc();
	spillBuffer = new SpillBuffer();
	
	if(conf.getMapreduce_combine_class() != null) 
	    memCombineFunc = new MemCombineFunc();
	spills = new ArrayList<SpillPiece>();
	
	mapOutputs = new ArrayList<Segment>();
	merges = new ArrayList<MergeAction>();
    }

    public void setBasicInfo(String taskId, boolean isMapRunning, String runningPhase) {
	this.taskId = taskId;
	this.isMapRunning = isMapRunning;
	this.runningPhase = runningPhase;
    }
    
    public void setInputSplit(Input input) {

	split.setSplitBytes(input.getSplitBytes());
	mapFunc.setTmapInputBytes(input.getSplitBytes());
    }
    
    public void setSpillBuffer(MapperBuffer buffer) {
	spillBuffer.setIoSortMB(conf.getIo_sort_mb());
	spillBuffer.setDataBuffer(buffer.getSoftBufferLimit(), buffer.getKvbufferBytes());
	spillBuffer.setRecordBuffer(buffer.getSoftRecordLimit(), buffer.getKvoffsetsLen());
    }
    
    public void setSpills(Spill spill, MapperCounters counters) {
	long inputRecsInPreviousSpills = 0;
	long outputRecsInPreviousSpills = 0;
	
	List<SpillInfo> list = spill.getSpillInfoList();
	
	SpillInfo info = null;
	for(int i = 0; i < list.size(); i++) {
	    info = list.get(i);
	    spills.add(new SpillPiece(info));
	    
	    if(i != list.size() - 1) {
		inputRecsInPreviousSpills += info.getRecordsBeforeCombine();
		outputRecsInPreviousSpills += info.getRecordsAfterCombine();
	    }
	 
	}
	
	if(memCombineFunc != null && info != null) {
	    memCombineFunc.settCombineInputRecords(info.getRecordsBeforeCombine());
	    
	    
	    memCombineFunc.setcCombineInputRecords(counters.getCombine_input_records() - inputRecsInPreviousSpills);
	    memCombineFunc.setcCombineOutputRecords(counters.getCombine_output_records() - outputRecsInPreviousSpills);
	    
	    if(info.getRecordsAfterCombine() != -1) {
		 memCombineFunc.setcCombineInputRecords(info.getRecordsBeforeCombine());
		 memCombineFunc.setcCombineOutputRecords(info.getRecordsAfterCombine());
	    }
	}
    }
    
    public void setMerges(Merge merge, MapperCounters counters) {
	long inputRecsInPreviousMerges = 0;
	long outputRecsInPreviousMerges = 0;
	
	List<MergeInfo> list = merge.getMergeInfoList();
	
	MergeInfo info = null;
	for(int i = 0; i < list.size(); i++) {
	    info = list.get(i);
	    merges.add(new MergeAction(info));
	    
	    if(i != list.size() - 1) {
		inputRecsInPreviousMerges += info.getRecordsBeforeMerge();
		outputRecsInPreviousMerges += info.getRecordsAfterMerge();
	    }
	}
	
	if(merge.hasCombine() == true)
	{
	    diskCombineFunc = new DiskCombineFunc();
	    diskCombineFunc.settCombineInputRecords(info.getRecordsBeforeMerge());
	    
	    long combineInputRecsInSpills = 0;
	    long combineOutputRecsInSpills = 0;
	    
	    for(SpillPiece sp : spills) {
		combineInputRecsInSpills += sp.getRecordsBefore();
		combineOutputRecsInSpills += sp.getRecordsAfter();
	    }
	    
	    diskCombineFunc.setcCombineInputRecords(counters.getCombine_input_records() 
		    - combineInputRecsInSpills - inputRecsInPreviousMerges);
	    diskCombineFunc.setcCombineOutputRecords(counters.getCombine_output_records() 
		    - combineOutputRecsInSpills - outputRecsInPreviousMerges);
	}
	 
	
    }
    
    public void setCounters(MapperCounters counters) {
	setMapFunc(counters);
    }

    private void setMapFunc(MapperCounters c) {
	mapFunc.setCmapInputBytes(c.getHdfs_bytes_read());
	mapFunc.setCmapInputRecords(c.getMap_input_records());
	
	mapFunc.setCmapOutputBytes(c.getMap_output_bytes());
	mapFunc.setCmapOutputRecords(c.getMap_output_records());
	
    }

    public String getTaskId() {
	return taskId;
    }

    public InputSplit getSplit() {
	return split;
    }

    public MapFunc getMapFunc() {
	return mapFunc;
    }
    
    public SpillBuffer getSpillBuffer() {
	return spillBuffer;
    }

    public MemCombineFunc getMemCombineFunc() {
	return memCombineFunc;
    }

    public List<SpillPiece> getSpills() {
	return spills;
    }

    public DiskCombineFunc getDiskCombineFunc() {
	return diskCombineFunc;
    }
   
    public List<Segment> getMapOutputs() {
	return mapOutputs;
    }

    public List<MergeAction> getMerges() {
	return merges;
    }

}
