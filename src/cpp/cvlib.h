/**
 * cvlib.h  
 * 
 * Header file for the cvlib native binding library to ClearVolume.
 *
 * @author Loic Royer 2014
 *
 */


/**
 * Initializes the libary which really means JVm initialization.
 * the path to the ClearVolume jar must be provided. It must be 
 * the non-executable jar packaged together with the library.
 */
extern "C" __declspec(dllexport) unsigned long 	__cdecl begincvlib(char* ClearVolumeJarPath);

/**
 * releases any ressource allocated during the utilization of cvlib.
 */
extern "C" __declspec(dllexport) unsigned long 	__cdecl endcvlib();

/**
 * Clears the last error cache.
 */
extern "C" __declspec(dllexport) void 			__cdecl clearError();

/**
 * Returns the last java exception error message if available.
 */
extern "C" __declspec(dllexport) char* 			__cdecl getLastJavaExceptionMessage();

/**
 * Returns the last error message if available. Returns the last java error message if available.
 */
extern "C" __declspec(dllexport) char* 			__cdecl getLastError();



/**
 * Creates a renderer with a given renderer ID. Any integer can be picked for an ID. This ID is used
 * as a handle to identify this renderer. The window width, height, max texture width and height, as well
 * as format of the data in bytes per voxel must be provided. A sink of the same ID number is made available.
 */
extern "C" __declspec(dllexport) long __cdecl createRenderer(				long pRendererId,
																			long pWindowWidth,
																			long pWindowHeight,
																			long pBytesPerVoxel,
																			long pMaxTextureWidth,
																			long pMaxTextureHeight);

/**
 * Destroys a given renderer.
 */
extern "C" __declspec(dllexport) long __cdecl destroyRenderer(				long pRendererId);

/**
 * Creates a server with a given renderer ID. Any integer can be picked for an ID. This ID is used
 * as a handle to identify this renderer. A sink of the same ID number is made available.
 */
extern "C" __declspec(dllexport) long __cdecl createServer(					long pServerId);

/**
 * Destroys a given server.
 */
extern "C" __declspec(dllexport) long __cdecl destroyServer(				long pServerId);


/**
 * Sets the current voxel dimensions of a given renderer in real units (um). The values provided are current after
 * calling this function. 
 */
extern "C"__declspec(dllexport) long __cdecl setVoxelDimensionsInRealUnits( long pSinkId,
																			double pVoxelWidthInRealUnits,
																			double pVoxelHeightInRealUnits,
																			double pVoxelDepthInRealUnits);

/**
 * Sets the current volume index and time (s) for a given renderer. The values provided are current after
 * calling this function. 
 */
extern "C"__declspec(dllexport) long __cdecl setVolumeIndexAndTime( 		long pSinkId,
																			long pVolumeIndex,
																			double pVolumeTimeInSeconds);

/**
 * Sends a volume (format: 8bit usignd integer) to a given sink (same id as correspodning renderer or server).
 * This volume is bound to a specific channel (view or color). Data must be provided in the form of a char* buffer. 
 * The volume dimensions in voxels must also be provided.
 */
extern "C" __declspec(dllexport) long __cdecl send8bitUINTVolumeDataToSink( long pSinkId,
																			long pChannelId,
																			char *pBufferAddress,
																			__int64 pBufferLength,
																			long pWidthInVoxels,
																			long pHeightInVoxels,
																			long pDepthInVoxels);

/**
 * Sends a volume (format: 16bit usignd integer) to a given sink (same id as correspodning renderer or server).
 * This volume is bound to a specific channel (view or color). Data must be provided in the form of a char* buffer. 
 * The volume dimensions in voxels must also be provided.
 */
extern "C"__declspec(dllexport) long __cdecl send16bitUINTVolumeDataToSink( long pSinkId,
																			long pChannelId,
																			short *pBufferAddress,
																			__int64 pBufferLength,																			
																			long pWidthInVoxels,
																			long pHeightInVoxels,
																			long pDepthInVoxels);