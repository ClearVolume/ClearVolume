package clearvolume.volume.sink.timeshift;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import clearvolume.ClearVolumeCloseable;
import clearvolume.volume.Volume;
import clearvolume.volume.VolumeManager;
import clearvolume.volume.sink.relay.RelaySinkAdapter;
import clearvolume.volume.sink.relay.RelaySinkInterface;

public class TimeShiftingSink extends RelaySinkAdapter implements
																											RelaySinkInterface,
																											ClearVolumeCloseable
{
	private static final float cCleanUpRatio = 0.25f;

	private static final ExecutorService mSeekingExecutor = Executors.newSingleThreadExecutor();
	private SwitchableSoftReferenceManager<Volume<?>> mSwitchableSoftReferenceManager;

	private Object mLock = new Object();
	private HashMap<Integer, TreeMap<Long, SwitchableSoftReference<Volume<?>>>> mChannelToVolumeListsMap = new HashMap<>();
	private TreeSet<Integer> mAvailableChannels = new TreeSet<>();

	private volatile long mSoftMemoryHorizonInTimePointIndices;
	private volatile long mHardMemoryHorizonInTimePointIndices;
	private volatile long mCleanUpPeriodInTimePoints;
	private volatile long mHighestTimePointIndexSeen = 0;
	private volatile long mTimeShift = 0;
	private volatile boolean mIsPlaying = true;

	public TimeShiftingSink(long pSoftMemoryHoryzonInTimePointIndices,
													long pHardMemoryHoryzonInTimePointIndices)
	{
		super();
		mSwitchableSoftReferenceManager = new SwitchableSoftReferenceManager<>();
		mSoftMemoryHorizonInTimePointIndices = Math.min(pSoftMemoryHoryzonInTimePointIndices,
																										pHardMemoryHoryzonInTimePointIndices);
		mHardMemoryHorizonInTimePointIndices = Math.max(pHardMemoryHoryzonInTimePointIndices,
																										pSoftMemoryHoryzonInTimePointIndices);
		mCleanUpPeriodInTimePoints = (long) (mSoftMemoryHorizonInTimePointIndices * cCleanUpRatio);
	}

	public void setTimeShiftNormalized(final double pTimeShiftNormalized)
	{
		mSeekingExecutor.execute(() -> {

			synchronized (mLock)
			{
			final long lPreviousTimeShift = mTimeShift;
			
			// find the available data interval to evade invalid indices
			final long startPos = Math.max(0, mHighestTimePointIndexSeen - mHardMemoryHorizonInTimePointIndices);
			final long interval = mHighestTimePointIndexSeen - startPos;
			
			System.err.println("interval=[" + startPos +"," +interval+"]");
			mTimeShift = -Math.round(interval * pTimeShiftNormalized);
			if (!mIsPlaying && lPreviousTimeShift != mTimeShift)
				for (int lChannel : mAvailableChannels)
					sendVolumeInternal(lChannel);
			}
		});
	}

	public void setTimeShift(long pTimeShift)
	{
		mTimeShift = pTimeShift;
	}

	public long getTimeShift()
	{
		return mTimeShift;
	}
	
	public long getHardMemoryHorizon() {
		return mHardMemoryHorizonInTimePointIndices;
	}
	
	public long getSoftMemoryHorizon() {
		return mSoftMemoryHorizonInTimePointIndices;
	}
	
	public long getNumberOfTimepoints() {
		return mHighestTimePointIndexSeen;
	}

	public int getNumberOfAvailableChannels()
	{
		return mAvailableChannels.size();
	}

	public int getAvailableChannels()
	{
		return mAvailableChannels.size();
	}

	@Override
	public void sendVolume(Volume<?> pVolume)
	{
		synchronized (mLock)
		{
			int lVolumeChannelID = pVolume.getChannelID();
			mAvailableChannels.add(lVolumeChannelID);
			TreeMap<Long, SwitchableSoftReference<Volume<?>>> lTimePointIndexToVolumeMapReference = mChannelToVolumeListsMap.get(lVolumeChannelID);

			if (lTimePointIndexToVolumeMapReference == null)
			{
				lTimePointIndexToVolumeMapReference = new TreeMap<>();
				mChannelToVolumeListsMap.put(	lVolumeChannelID,
																			lTimePointIndexToVolumeMapReference);
			}


			lTimePointIndexToVolumeMapReference.put(pVolume.getTimeIndex(),
																							wrapWithReference(pVolume));

			mHighestTimePointIndexSeen = Math.max(mHighestTimePointIndexSeen,
																						pVolume.getTimeIndex());

			if (mIsPlaying)
				sendVolumeInternal(lVolumeChannelID);

			cleanUpOldVolumes(mHighestTimePointIndexSeen, lVolumeChannelID);
		}
	}

	private void sendVolumeInternal(int lVolumeChannelID)
	{
		synchronized (mLock)
		{
			Volume<?> lVolumeToSend = getVolumeToSend(lVolumeChannelID);		

			if (lVolumeToSend != null) {
				getRelaySink().sendVolume(lVolumeToSend);
			} else {
				System.err.println("Did not have any volume to send :(");
			}

			cleanUpOldVolumes(mHighestTimePointIndexSeen, lVolumeChannelID);
		}
	}

	private SwitchableSoftReference<Volume<?>> wrapWithReference(Volume<?> pVolume)
	{
		return mSwitchableSoftReferenceManager.wrapReference(	pVolume,
																													() -> {
																														System.out.println("CLEANING!");
																														pVolume.makeAvailableToManager();
																													});
	}

	private Volume<?> getVolumeToSend(int pVolumeChannelID)
	{
		synchronized (mLock)
		{
			TreeMap<Long, SwitchableSoftReference<Volume<?>>> lTimePointIndexToVolumeMap = mChannelToVolumeListsMap.get(pVolumeChannelID);

			if (lTimePointIndexToVolumeMap.isEmpty())
				return null;

			Entry<Long, SwitchableSoftReference<Volume<?>>> lIndexVolumeEntry = lTimePointIndexToVolumeMap.floorEntry(mHighestTimePointIndexSeen + mTimeShift);
			if (lIndexVolumeEntry == null)
				lIndexVolumeEntry = lTimePointIndexToVolumeMap.ceilingEntry(mHighestTimePointIndexSeen + mTimeShift);


			if (lIndexVolumeEntry == null)
			{
				System.out.println();
				return null;
			}

			Volume<?> lVolume = lIndexVolumeEntry.getValue().get();
			if (lVolume == null)
			{
				lTimePointIndexToVolumeMap.remove(lIndexVolumeEntry.getKey());
				return getVolumeToSend(pVolumeChannelID);
			}
			return lVolume;
		}
	}

	private void cleanUpOldVolumes(long pTimePointIndex, int pChannelID)
	{
		synchronized (mLock)
		{
			if (pTimePointIndex % mCleanUpPeriodInTimePoints != 0)
				return;

			TreeMap<Long, SwitchableSoftReference<Volume<?>>> lTimePointIndexToVolumeMap = mChannelToVolumeListsMap.get(pChannelID);
			if (lTimePointIndexToVolumeMap.isEmpty())
			{
				mChannelToVolumeListsMap.remove(pChannelID);
				return;
			}
			Long lLastTimePoint = lTimePointIndexToVolumeMap.lastKey();

			Long lTimePoint = lLastTimePoint;
			while (lTimePoint != null && lTimePoint > lLastTimePoint - mSoftMemoryHorizonInTimePointIndices)
			{
				lTimePoint = lTimePointIndexToVolumeMap.lowerKey(lTimePoint);
			}
			while (lTimePoint != null && lTimePoint > lLastTimePoint - mHardMemoryHorizonInTimePointIndices)
			{
				SwitchableSoftReference<Volume<?>> lSwitchableSoftReference = lTimePointIndexToVolumeMap.get(lTimePoint);
				if (lSwitchableSoftReference.isGone())
				{
					lTimePointIndexToVolumeMap.remove(lTimePoint);
					continue;
				}
				lSwitchableSoftReference.soften();
				lTimePoint = lTimePointIndexToVolumeMap.lowerKey(lTimePoint);
			}
			while (lTimePoint != null)
			{
				SwitchableSoftReference<Volume<?>> lSwitchableSoftReference = lTimePointIndexToVolumeMap.get(lTimePoint);
				Volume<?> lVolume = lSwitchableSoftReference.get();
				if (lVolume != null)
					lVolume.makeAvailableToManager();
				lTimePointIndexToVolumeMap.remove(lTimePoint);
				lTimePoint = lTimePointIndexToVolumeMap.lowerKey(lTimePoint);
			}
		}
	}

	@Override
	public VolumeManager getManager()
	{
		return getRelaySink().getManager();
	}

	@Override
	public void close()
	{
		synchronized (mLock)
		{
			for (Map.Entry<Integer, TreeMap<Long, SwitchableSoftReference<Volume<?>>>> lTimeLineForChannelEntry : mChannelToVolumeListsMap.entrySet())
			{
				TreeMap<Long, SwitchableSoftReference<Volume<?>>> lTimeLineTreeMap = lTimeLineForChannelEntry.getValue();

				for (Map.Entry<Long, SwitchableSoftReference<Volume<?>>> lTimePointEntry : lTimeLineTreeMap.entrySet())
				{
					SwitchableSoftReference<Volume<?>> lVolumeSoftReference = lTimePointEntry.getValue();
					Volume<?> lVolume = lVolumeSoftReference.get();
					if (lVolume != null)
						lVolume.close();
					lVolumeSoftReference.soften();
				}

				lTimeLineTreeMap.clear();
			}
			mChannelToVolumeListsMap.clear();
			mChannelToVolumeListsMap = null;

			mAvailableChannels.clear();
		}

	}

	public void pause()
	{
		mIsPlaying = false;
	}

	public void play()
	{
		mIsPlaying = true;
	}

}
