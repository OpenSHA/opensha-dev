package scratch.ned.GK_Declustering;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.dom4j.DocumentException;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc_3D;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.params.PtSrcDistanceCorrectionParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.Declustering.GardnerKnopoffDeclustering;
import org.opensha.sha.earthquake.observedEarthquake.parsers.USGS_NSHMP_CatalogParser;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.utils.PtSrcDistCorr;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.math.Quantiles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.GardnerKnopoffAftershockFilter;
import scratch.ned.FSS_Inversion2019.FaultSystemRuptureRateInversion;
import scratch.ned.FSS_Inversion2019.PlottingUtils;

public class U3ETAS_SimulationAnalysis {
	
	static final boolean D = true; // debug flag
	
	static String fssFileName = "/Users/field/workspace/git/opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip";
	static String catalogsFileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/DeclusteringAnalysis/Data/U3ETAS_SimulationData/results_m5_preserve_chain_FullTD_totRateScaleFactor1pt14.bin"; 
//	static String catalogsFileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/DeclusteringAnalysis/Data/U3ETAS_SimulationData/results_m5_preserve_chain_FullTD_totRateScaleFactor1pt5.bin.bin"; 
//	static String catalogsFileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/DeclusteringAnalysis/Data/U3ETAS_SimulationData/results_m5_preserve_chain_NoERT.bin"; 
	static double catalogDuration=500; // hard coded
	static double catalogStartYear=2012;
	static long millisPerYr = (long)(1000*60*60*24*365.25);

	
	ArrayList<ObsEqkRupList> catalogList;
	ArrayList<ObsEqkRupList> catalogDeclusteredList;
	ArrayList<ObsEqkRupList> catalogRandomizedList;
	static double minMag = 5.05;
	static double deltaMag = 0.1;
	static int numMag = 40;
	
	static double hazCurveLnMin = Math.log(0.001);
	static double hazCurveLnMax = Math.log(10);
	static int hazCurveNum = 40;
	static double hazCurveDelta = (hazCurveLnMax-hazCurveLnMin)/(double)(hazCurveNum-1);

//	ScalarIMR imr;
	
	
	public U3ETAS_SimulationAnalysis() {
		
		try {
			this.catalogList = loadCatalogs(new File(fssFileName), new File(catalogsFileName));
		} catch (IOException | DocumentException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
//		System.out.println("num Catalogs = "+catalogList.size());
		
		catalogDeclusteredList = getGK_DeclusteredCatalog(catalogList);
		if(D) System.out.println("Done Declustering");
		

	}
	
	
	public static ArrayList<ObsEqkRupList> getGK_DeclusteredCatalog(ArrayList<ObsEqkRupList> catalogList) {
		
		ArrayList<ObsEqkRupList> declusteredList = new ArrayList<ObsEqkRupList>();
		for(ObsEqkRupList rupList: catalogList) {
			ObsEqkRupList declusteredCatalog = GardnerKnopoffDeclustering.getDeclusteredCatalog(rupList);
			declusteredList.add(declusteredCatalog);
		}
		return declusteredList;
	}
	
	
	
	public ArrayList<ObsEqkRupList> getRandomizedCatalogs() {
		if(catalogRandomizedList==null)
			catalogRandomizedList = getRandomizedCatalogs(this.getCatalogs(), null);
		return catalogRandomizedList;
	}

	

	/**
	 * This not only randomizes event times within each catalog, but also among catalogs
	 * so there isn't a high variance of total rates among catalogs.  This clones the
	 * ruptures and modifies the origin times.
	 */
	public static ArrayList<ObsEqkRupList> getRandomizedCatalogs(ArrayList<ObsEqkRupList> catalogList, Random random) {
		if(random==null)
			random = new Random();
		ArrayList<ObsEqkRupList> catalogRandomizedList = new ArrayList<ObsEqkRupList>();
		for(int i=0;i<catalogList.size();i++)
			catalogRandomizedList.add(new ObsEqkRupList());
		for(ObsEqkRupList catalog:catalogList) {
			for(ObsEqkRupture rup:catalog) {
				int ithCatalog = (int)Math.floor(random.nextDouble()*catalogList.size());
				double randomTimeYr = catalogStartYear+random.nextDouble()*catalogDuration;
				if(randomTimeYr<catalogStartYear || randomTimeYr>catalogStartYear+catalogDuration)
					throw new RuntimeException("ERROR: "+randomTimeYr);
//				rup.setOriginTime((long)((double)millisPerYr*(randomTimeYr-1970)));
				ObsEqkRupture newRup = (ObsEqkRupture) rup.clone();
				newRup.setOriginTime((long)((double)millisPerYr*(randomTimeYr-1970)));
//				catalogRandomizedList.get(ithCatalog).add(rup);
				catalogRandomizedList.get(ithCatalog).add(newRup);
			}
		}
		for(int i=0;i<catalogRandomizedList.size();i++) {
			catalogRandomizedList.get(i).sortByOriginTime();
			System.out.println(catalogRandomizedList.get(i).size()+" events in randomized catalog "+i);
		}
//		catalogList = catalogRandomizedList;
		return catalogRandomizedList;
	}
	
	

	
	public void doMFDs() {
		IncrementalMagFreqDist mfd = makeMFD(catalogList);
		double bVal = Math.log10(mfd.getY(5.05)/mfd.getY(6.95))/(6.95-5.05);
		mfd.setInfo("bVal = "+(float)bVal);
		
		IncrementalMagFreqDist mfdDeclustered = makeMFD(catalogDeclusteredList);
		bVal = Math.log10(mfdDeclustered.getY(5.05)/mfdDeclustered.getY(6.95))/(6.95-5.05);
		mfdDeclustered.setInfo("bVal = "+(float)bVal);

		
		IncrementalMagFreqDist ratio = makeMFD();
		for(int i=0;i<ratio.size();i++)
			ratio.add(i, mfdDeclustered.getY(i)/mfd.getY(i));
		
		// cumulative MFDs
		EvenlyDiscretizedFunc cumMFD = mfd.getCumRateDistWithOffset();
		bVal = Math.log10(cumMFD.getClosestYtoX(5.0)/cumMFD.getClosestYtoX(6.9))/(6.9-5.0);
		cumMFD.setInfo("bVal = "+(float)bVal);
		
		EvenlyDiscretizedFunc cumMFD_Declustered = mfdDeclustered.getCumRateDistWithOffset();
		bVal = Math.log10(cumMFD_Declustered.getClosestYtoX(5.0)/cumMFD_Declustered.getClosestYtoX(6.9))/(6.9-5.0);
		cumMFD_Declustered.setInfo("bVal = "+(float)bVal);

		
		ArrayList<XY_DataSet> funcs = new  ArrayList<XY_DataSet>();
		funcs.add(mfd);
		funcs.add(cumMFD);
		funcs.add(mfdDeclustered);
		funcs.add(cumMFD_Declustered);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.RED));
//		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
//		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
		Range xRange = new Range(5, 9);
		Range yRange = new Range(1e-5, 20.);
		PlottingUtils.writeAndOrPlotFuncs(funcs, plotChars, null, "Magnitude", "Num", xRange, yRange, 
				false, true, 3.5, 3.0, null, true);
		
		
		// ratio plot
		ArrayList<XY_DataSet> funcs2 = new  ArrayList<XY_DataSet>();
		funcs2.add(ratio);
		GardnerKnopoffAftershockFilter gkFilter = new GardnerKnopoffAftershockFilter(0.05, 9.95, 100);
		funcs2.add(gkFilter);
		ArrayList<PlotCurveCharacterstics> plotChars2 = new ArrayList<PlotCurveCharacterstics>();
		plotChars2.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars2.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));
		Range yRange2 = new Range(0, 1.1);
		PlottingUtils.writeAndOrPlotFuncs(funcs2, plotChars2, null, "Magnitude", "Num", xRange, yRange2, 
				false, false, 3.5, 3.0, null, true);
	}
	
	
	public static ArrayList<ObsEqkRupList> loadCatalogs(File fssFile, File catalogsFile) throws IOException, DocumentException {
		

		FaultSystemSolution sol = FaultSystemIO.loadSol(fssFile);;
		List<ETAS_Catalog> catalogs = ETAS_CatalogIO.loadCatalogsBinary(catalogsFile, 5d); // 5d will filter out below M5
		
		// temporary hack
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		
		FaultSystemSolutionERF_ETAS erf = ETAS_Launcher.buildERF(sol,false, 1d, 2012);
		erf.updateForecast();

		// set the finite rupture surfaces
		String errorMessage = null;
		for (ETAS_Catalog catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				int nth = rup.getNthERF_Index();
				EqkRupture nthRup = erf.getNthRupture(nth);
				
				// verify that magnitudes are the same
				try {
					Preconditions.checkState((float)rup.getMag() == (float)nthRup.getMag(), "Nth rupture mag=%s doesn't match ETAS mag=%s",
							nthRup.getMag(), rup.getMag());
				} catch (Exception e) {
					if(errorMessage == null)
						errorMessage = "ERROR: one or more magnitudes did not match";
//					System.out.println("Error: "+(float)rup.getMag()+"\t"+(float)nthRup.getMag()+"\t"+
//							rup.getHypocenterLocation()+"\t"+
//							nthRup.getHypocenterLocation());
//					e.printStackTrace();
//					System.exit(0);
				}
				
				// replace surface if it's a FSS rupture
				if(rup.getFSSIndex() != -1) {
					rup.setRuptureSurface(nthRup.getRuptureSurface());
					rup.setAveRake(nthRup.getAveRake());
				}
				else {
					rup.setAveRake(0.);
					rup.setPointSurface(rup.getHypocenterLocation(), 0., 90.);
				}
			}
		}
		
		if(D && errorMessage != null)
			System.out.println(errorMessage);

				
		// convert to a list of ObsEqkRupList objects (and filter M≤5 events out)
		ArrayList<ObsEqkRupList> obsEqkRupListList = new ArrayList<ObsEqkRupList>();
		
		for (ETAS_Catalog catalog : catalogs) {
			ObsEqkRupList obsEqkRupList = new ObsEqkRupList();
			for (ETAS_EqkRupture rup : catalog) {
				if(rup.getMag()>=5.)
					obsEqkRupList.add(rup);
			}
			obsEqkRupListList.add(obsEqkRupList);
		}
		
		return obsEqkRupListList;
	}
	
	
	
	private static IncrementalMagFreqDist makeMFD() {
		IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(minMag, numMag, deltaMag);
		mfd.setTolerance(deltaMag);
		return mfd;
	}
	
	
	private static IncrementalMagFreqDist makeMFD(ArrayList<ObsEqkRupList> catalogList) {
		IncrementalMagFreqDist mfd = makeMFD();
		for(ObsEqkRupList rupList: catalogList)
			addToMFD(rupList, mfd);
		mfd.scale(1.0/(catalogList.size()*catalogDuration));
		return mfd;
	}

	
	

	
	private static IncrementalMagFreqDist makeMagNumDist(ObsEqkRupList rupList) {
		IncrementalMagFreqDist mfd = makeMFD();
		for(ObsEqkRupture rup:rupList)
			mfd.add(rup.getMag(), 1.0);
		return mfd;
	}

	
	


	private static void addToMFD(ObsEqkRupList rupList, IncrementalMagFreqDist mfd) {
		for (ObsEqkRupture rup : rupList) {
			if(rup.getMag()>1)
				mfd.add(rup.getMag(), 1.0);
		}
	}
	
	private static String getIMT_String(double saPeriod) {
		String imtString = "PGA";
		if(saPeriod != 0)
			imtString = saPeriod+"secSA";
		return imtString;
	}
	
	
	/**
	 * 
	 * @param catalogList
	 * @param location
	 * @param duration
	 * @param saPeriod
	 * @return - the first array element has the mean, min, and max and the second has the 
	 * mean, lower95 conf of the mean, and upper 95 conf of the mean
	 */
	public static UncertainArbDiscDataset[] computeHazardCurvesFromCatalogs(ArrayList<ObsEqkRupList> catalogList, Location location, 
			double duration, double saPeriod, boolean randomIML, ScalarIMR imr) {
		
		// get subcatalogs
		ArrayList<ObsEqkRupList> eqkRupListList = getSubcatalogList(catalogList, duration);		
		
		ArbDiscrEmpiricalDistFunc_3D curvesFromAllCatalogsFunc_3D = new ArbDiscrEmpiricalDistFunc_3D(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
//		ArrayList<XY_DataSet> tempFuncList = new ArrayList<XY_DataSet>();
//		int maxNum =50;
//		int num=1;
		
		int numLoops = 1;
		if(randomIML) numLoops = 10; // get 10 IML sample
		for(int k=0;k<numLoops; k++) {
			int showProgressAt = eqkRupListList.size()/10;
			for(int i=0;i<eqkRupListList.size();i++) {
				if(i>showProgressAt && D) {
					System.out.println(i);
					showProgressAt+=eqkRupListList.size()/10;
				}
				EvenlyDiscretizedFunc func = computeHazardCurveLnX(eqkRupListList.get(i), location, saPeriod, duration, randomIML, imr);
				curvesFromAllCatalogsFunc_3D.set(func, 1.0);
				
//				if(num<maxNum) {
//					tempFuncList.add(func);
//					num+=1;
//				}
			}			
		}
		
//		tempFuncList.add(curvesFromAllCatalogsFunc_3D.getMeanCurve());
//		tempPlotAllHazardCurves(tempFuncList, duration, null, true, "Test");
			

		EvenlyDiscretizedFunc hazCurveMeanLnX = curvesFromAllCatalogsFunc_3D.getMeanCurve();
		EvenlyDiscretizedFunc hazCurveMinLnX = curvesFromAllCatalogsFunc_3D.getMinCurve();
		EvenlyDiscretizedFunc hazCurveMaxLnX = curvesFromAllCatalogsFunc_3D.getMaxCurve();
		UncertainArbDiscDataset hazCurveMean95confLnX = get95perConfForMultRuns(curvesFromAllCatalogsFunc_3D);

		ArbitrarilyDiscretizedFunc hazCurveMean = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc hazCurveMin = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc hazCurveMax = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc hazCurveMeanLower95 = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc hazCurveMeanUpper95 = new ArbitrarilyDiscretizedFunc();
		for(int i=0;i<hazCurveNum;i++) {
			hazCurveMean.set(Math.exp(hazCurveMeanLnX.getX(i)), hazCurveMeanLnX.getY(i));
			hazCurveMax.set(Math.exp(hazCurveMaxLnX.getX(i)), hazCurveMaxLnX.getY(i));
			hazCurveMin.set(Math.exp(hazCurveMinLnX.getX(i)), hazCurveMinLnX.getY(i));
			hazCurveMeanLower95.set(Math.exp(hazCurveMean95confLnX.getX(i)), hazCurveMean95confLnX.getLowerY(i));
			hazCurveMeanUpper95.set(Math.exp(hazCurveMean95confLnX.getX(i)), hazCurveMean95confLnX.getUpperY(i));
		}

		hazCurveMean.setName("hazCurveMean");
		UncertainArbDiscDataset hazCurveMinMaxRange = new UncertainArbDiscDataset(hazCurveMean, hazCurveMin, hazCurveMax);
		hazCurveMinMaxRange.setName("hazCurveMinMaxRange");
		UncertainArbDiscDataset hazCurveMean95conf = new UncertainArbDiscDataset(hazCurveMean, hazCurveMeanLower95, hazCurveMeanUpper95);
		hazCurveMean95conf.setName("hazCurveMean95conf");
		
		
		//
		double twoIn50prob = 1.0 - Math.exp(-duration/2475.);
		double tenIn50prob = 1.0 - Math.exp(-duration/475.);
		
		AbstractDiscretizedFunc funcLower = (AbstractDiscretizedFunc) hazCurveMean95confLnX.getLower(); // need to cast to get the interpolation function below
		AbstractDiscretizedFunc funcUpper = (AbstractDiscretizedFunc) hazCurveMean95confLnX.getUpper();
		
		double twoIn50value = Math.exp(hazCurveMeanLnX.getFirstInterpolatedX_inLogYDomain(twoIn50prob));
		double twoIn50valueLower95 = Math.exp(funcLower.getFirstInterpolatedX_inLogYDomain(twoIn50prob));
		double twoIn50valueUpper95 = Math.exp(funcUpper.getFirstInterpolatedX_inLogYDomain(twoIn50prob));
		
		double tenIn50value = Math.exp(hazCurveMeanLnX.getFirstInterpolatedX_inLogYDomain(tenIn50prob));
		double tenIn50valueLower95 = Math.exp(funcLower.getFirstInterpolatedX_inLogYDomain(tenIn50prob));
		double tenIn50valueUpper95 = Math.exp(funcUpper.getFirstInterpolatedX_inLogYDomain(tenIn50prob));
		
		String info = getIMT_String(saPeriod) + 
				"\n\t2in50 value: "+(float)twoIn50value+" (95conf: "+(float)twoIn50valueLower95+" to "+(float)twoIn50valueUpper95+"); Prob in "+duration+" yrs is "+(float)twoIn50prob+
				"\n\t10in50 value: "+(float)tenIn50value+" (95conf: "+(float)tenIn50valueLower95+" to "+(float)tenIn50valueUpper95+"); Prob in "+duration+" yrs is "+(float)tenIn50prob+"\n";
		hazCurveMean95conf.setInfo(info+"\nLocation: "+location.getLatitude()+", "+location.getLongitude());

		
//		// Compute hazard curve from TI ERF
//		EvenlyDiscretizedFunc erfHazCurveLnX = computeHazardCurveLnX(getTimeIndERF_Instance(duration), location, saPeriod, duration);
//		// convert to linear x valules
//		ArbitrarilyDiscretizedFunc erfHazCurveLinearXvalues = new ArbitrarilyDiscretizedFunc();
//		for (int i = 0; i < erfHazCurveLnX.size(); ++i)
//			erfHazCurveLinearXvalues.set(Math.exp(erfHazCurveLnX.getX(i)), erfHazCurveLnX.getY(i));
//		// make the plot
//		plottingFuncsArray.add(erfHazCurveLinearXvalues);	
//		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));

		return new UncertainArbDiscDataset[] {hazCurveMinMaxRange,hazCurveMean95conf};
	}
	
	
	/**
	 * This computes ...
	 * @param catalogList
	 * @param location
	 * @param duration
	 * @param saPeriod
	 * @return - ArrayList of Cumulative Num Distributions (one for each iml in the given array)
	 */
	public ArrayList<ArbitrarilyDiscretizedFunc>  computeNumHazardExceedDist(ArrayList<ObsEqkRupList> catalogList, Location location, 
			double duration, double saPeriod, double[] imlLinearArray, ScalarIMR imr) {
		
		ArrayList<ArbitrarilyDiscretizedFunc> funcsList = new ArrayList<ArbitrarilyDiscretizedFunc>();
				
		// get subcatalogs
		ArrayList<ObsEqkRupList> eqkRupListList = getSubcatalogList(catalogList, duration);
		
		ArrayList<XY_DataSet> expNumVsTimeFuncList = new ArrayList<XY_DataSet>();
		for(int i=0;i<imlLinearArray.length;i++) {
			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(25.,eqkRupListList.size(),50.);
			func.setName("Exp Num Exceedances vs Time for IML = "+imlLinearArray[i]);
			expNumVsTimeFuncList.add(func);
		}

		
		ArbDiscrEmpiricalDistFunc_3D curvesFromAllCatalogsFunc_3D = new ArbDiscrEmpiricalDistFunc_3D(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		int showProgressAt = eqkRupListList.size()/10;
		for(int i=0;i<eqkRupListList.size();i++) {
			if(i>showProgressAt && D) {
				if(D) System.out.println(i);
				showProgressAt+=eqkRupListList.size()/10;
			}
			EvenlyDiscretizedFunc func = computeExpNumExceedCurveLnX(eqkRupListList.get(i), location, saPeriod, duration, imr);
			curvesFromAllCatalogsFunc_3D.set(func, 1.0);
			
			for(int j=0;j<imlLinearArray.length;j++) {
				double iml_ln = Math.log(imlLinearArray[j]);
				int iml_index=func.getClosestXIndex(iml_ln);
				expNumVsTimeFuncList.get(j).set(i,func.getY(iml_index));
			}
		}		
		
		// ***************************
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.MAGENTA));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.CYAN));
		PlottingUtils.writeAndOrPlotFuncs(expNumVsTimeFuncList, plotChars, "", "Time (yrs)", "Exp Num Exceedances", 
				new Range(0,50e3), new Range(1e-3,350), false, true, null, true);
		// ***************************

		
		EvenlyDiscretizedFunc meanExpNumExceedCurveLnX = curvesFromAllCatalogsFunc_3D.getMeanCurve();
		
		for(double imlLinear:imlLinearArray) {
			double iml_ln = Math.log(imlLinear);
			int iml_index=meanExpNumExceedCurveLnX.getClosestXIndex(iml_ln);
			double iml = (float)Math.exp(meanExpNumExceedCurveLnX.getX(iml_index));
			String name = "NumCDF for IML="+iml;
			double expNum = meanExpNumExceedCurveLnX.getY(iml_index);
			System.out.println(name+" for which expNum = "+(float)expNum);
			ArbitrarilyDiscretizedFunc numCDF = curvesFromAllCatalogsFunc_3D.getArbDiscrEmpDistFuncArray()[iml_index].getNormalizedCumDist();
			numCDF.setName(name+"; ExpNum="+expNum);

			int maxNum = (int)Math.floor(numCDF.getMaxX())+1;
			double minNum = numCDF.getMinX();
			
			EvenlyDiscretizedFunc numPDF_Discretized = new EvenlyDiscretizedFunc(0.,maxNum,1.0);
			boolean first = true;
			for(int i=0;i<numPDF_Discretized.size()-1;i++) {
				if(i+0.5<minNum)
					numPDF_Discretized.set(i,0);
				else if(first) {
					numPDF_Discretized.set(i,numCDF.getInterpolatedY((double)i+0.5));
					first = false;
				}
				else
					numPDF_Discretized.set(i,numCDF.getInterpolatedY((double)i+0.5)-numCDF.getInterpolatedY((double)i-0.5));
			}
			int i_last = numPDF_Discretized.size()-1;
			if(i_last>0)
				numPDF_Discretized.set(i_last,numCDF.getY(numCDF.size()-1)-numCDF.getInterpolatedY(i_last-0.5));
			numPDF_Discretized.setName("numPDF_Discretized; for ExpNum="+expNum);
			
			EvenlyDiscretizedFunc poissNumDist = new EvenlyDiscretizedFunc(0.,maxNum,1.0);
			PoissonDistribution poisDist = new PoissonDistribution(expNum);
			for(int i=0;i<poissNumDist.size();i++) {
				poissNumDist.set(i,poisDist.probability(i));
			}
			poissNumDist.setName("Poisson for ExpNum="+expNum);
			
			EvenlyDiscretizedFunc numPDF_Alt = new EvenlyDiscretizedFunc(0.,maxNum,1.0);
			double sum=0;
			double lastSum = 0;
			double upperBound = Double.NaN;
			for(int i=0;i<poissNumDist.size();i++) {
				sum+= poissNumDist.getY(i);
				if(sum>numCDF.getMinY()) {
					upperBound = numCDF.getFirstInterpolatedX(sum);
					System.out.println(i+" upperBound="+upperBound);
					numPDF_Alt.set(i,sum-lastSum);
				}
				lastSum=sum;
			}	
			
			ArrayList<XY_DataSet> plottingFuncsArray = new ArrayList<XY_DataSet>();
//			plottingFuncsArray.add(numPDF_Alt);
			plottingFuncsArray.add(numCDF);
			plottingFuncsArray.add(poissNumDist);
			plottingFuncsArray.add(numPDF_Discretized);
			this.quickPlot(plottingFuncsArray, "Num Exceedances","Probability",name);
			
			funcsList.add(numCDF);

		}
		
		
		return funcsList;
		
	}

	
	/**
	 * This computes ...
	 * @param catalogList
	 * @param location
	 * @param duration
	 * @param saPeriod
	 * @param imtLinearArray
	 * @return
	 */
	public ArrayList<ArbitrarilyDiscretizedFunc> computeNumHazardExceedDistRandomIML(ArrayList<ObsEqkRupList> catalogList, Location location, 
			double duration, double saPeriod, double[] imlLinearArray, ScalarIMR imr) {
		
		ArrayList<ArbitrarilyDiscretizedFunc> funcsList = new ArrayList<ArbitrarilyDiscretizedFunc>();
		
		// get subcatalogs
		ArrayList<ObsEqkRupList> eqkRupListList = getSubcatalogList(catalogList, duration);
		
		ArrayList<XY_DataSet> expNumVsTimeFuncList = new ArrayList<XY_DataSet>();
		for(int i=0;i<imlLinearArray.length;i++) {
			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(25.,eqkRupListList.size(),50.);
			func.setName("Exp Num Exceedances vs Time for IML = "+imlLinearArray[i]);
			expNumVsTimeFuncList.add(func);
		}
		
		ArbDiscrEmpiricalDistFunc_3D curvesFromAllCatalogsFunc_3D = new ArbDiscrEmpiricalDistFunc_3D(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		int showProgressAt = eqkRupListList.size()/10;
		for(int i=0;i<eqkRupListList.size();i++) {
			if(i>showProgressAt && D) {
				if(D) System.out.println(i);
				showProgressAt+=eqkRupListList.size()/10;
			}
			EvenlyDiscretizedFunc func = computeNumExceedCurveRandomIML_LnX(eqkRupListList.get(i), location, saPeriod, duration, imr);
			curvesFromAllCatalogsFunc_3D.set(func, 1.0);
			
			for(int j=0;j<imlLinearArray.length;j++) {
				double iml_ln = Math.log(imlLinearArray[j]);
				int iml_index=func.getClosestXIndex(iml_ln);
				expNumVsTimeFuncList.get(j).set(i,func.getY(iml_index));
			}

		}		
		
		// ***************************
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.MAGENTA));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.CYAN));
		PlottingUtils.writeAndOrPlotFuncs(expNumVsTimeFuncList, plotChars, "", "Time (yrs)", "Exp Num Exceedances", 
				new Range(0,50e3), new Range(1e-3,350), false, true, null, true);
		// ***************************

		
		EvenlyDiscretizedFunc meanExpNumExceedCurveLnX = curvesFromAllCatalogsFunc_3D.getMeanCurve();
		
		for(double imlLinear:imlLinearArray) {
			double iml_ln = Math.log(imlLinear);
			int iml_index=meanExpNumExceedCurveLnX.getClosestXIndex(iml_ln);
			double iml = (float)Math.exp(meanExpNumExceedCurveLnX.getX(iml_index));
			String name = "NumPDF for IML="+(float)iml;
			double expNum = meanExpNumExceedCurveLnX.getY(iml_index);
			System.out.println(name+" for which expNum = "+(float)expNum);
			ArbitrarilyDiscretizedFunc numPDF = curvesFromAllCatalogsFunc_3D.getArbDiscrEmpDistFuncArray()[iml_index].getNormalizedDist();
//			System.out.println(numPDF);

			numPDF.setName(name+"; ExpNum="+expNum);
			
			funcsList.add(numPDF);

			int maxNum = (int)Math.round(numPDF.getMaxX());
			EvenlyDiscretizedFunc poissCumNumDist = new EvenlyDiscretizedFunc(0.,maxNum+1,1.0);
			PoissonDistribution poisDist = new PoissonDistribution(expNum);
			for(int i=0;i<poissCumNumDist.size();i++) {
				poissCumNumDist.set(i,poisDist.probability(i));
			}
			poissCumNumDist.setName("Poisson for ExpNum="+expNum);
			
			ArrayList<XY_DataSet> plottingFuncsArray = new ArrayList<XY_DataSet>();
			plottingFuncsArray.add(poissCumNumDist);
			plottingFuncsArray.add(numPDF);
			this.quickPlot(plottingFuncsArray, "Num Exceedances","Probability",name);
		}
		
		return funcsList;
		
		
	}

	
	/**
	 * This computes the hazard curve from the average expected number of exceedances computed over
	 * all catalogs.
	 * @param catalogList
	 * @param location
	 * @param duration
	 * @param saPeriod
	 * @return
	 */
	public static ArbitrarilyDiscretizedFunc computeHazardCurvesFromCatalogsPoisson(ArrayList<ObsEqkRupList> catalogList, Location location, 
			double duration, double saPeriod, ScalarIMR imr) {
		
		// get subcatalogs
		ArrayList<ObsEqkRupList> eqkRupListList = getSubcatalogList(catalogList, duration);
		
		ArbDiscrEmpiricalDistFunc_3D curvesFromAllCatalogsFunc_3D = new ArbDiscrEmpiricalDistFunc_3D(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		int showProgressAt = eqkRupListList.size()/10;
		for(int i=0;i<eqkRupListList.size();i++) {
			if(i>showProgressAt && D) {
				if(D) System.out.println(i);
				showProgressAt+=eqkRupListList.size()/10;
			}
			EvenlyDiscretizedFunc func = computeExpNumExceedCurveLnX(eqkRupListList.get(i), location, saPeriod, duration, imr);
			curvesFromAllCatalogsFunc_3D.set(func, 1.0);
		}		
		
		EvenlyDiscretizedFunc meanExpNumExceedCurveLnX = curvesFromAllCatalogsFunc_3D.getMeanCurve();
		
//		System.out.println(meanExpNumExceedCurveLnX);
		
		ArbitrarilyDiscretizedFunc hazCurve = new ArbitrarilyDiscretizedFunc();

		for(int i=0;i<hazCurveNum;i++) {
			hazCurve.set(Math.exp(meanExpNumExceedCurveLnX.getX(i)), 1.0-Math.exp(-meanExpNumExceedCurveLnX.getY(i)));
		}

		hazCurve.setName("ComputeHazardCurvesFromCatalogsPoisson");
		hazCurve.setInfo("Hazard curve computed from expected num exceedances (using Poisson model)");
		
		
		

		return hazCurve;
	}

	
	public void computeNumEventDistribution(ArrayList<ObsEqkRupList> catalogList, double duration, double[] magArray) {
		
		// get subcatalogs
		ArrayList<ObsEqkRupList> eqkRupListList = getSubcatalogList(catalogList, duration);
		
		EvenlyDiscretizedFunc tempFunc = makeMagNumDist(eqkRupListList.get(0)).getCumRateDistWithOffset();
		ArbDiscrEmpiricalDistFunc_3D cumMFD_FromAllCatalogsFunc_3D = new ArbDiscrEmpiricalDistFunc_3D(tempFunc.getMinX(), tempFunc.size(), tempFunc.getDelta());
		
		int showProgressAt = eqkRupListList.size()/10;
		for(int i=0;i<eqkRupListList.size();i++) {
			if(i>showProgressAt && D) {
				if(D) System.out.println(i);
				showProgressAt+=eqkRupListList.size()/10;
			}
			IncrementalMagFreqDist func = makeMagNumDist(eqkRupListList.get(i));
			cumMFD_FromAllCatalogsFunc_3D.set(func.getCumRateDistWithOffset(), 1.0);
		}		
		
		EvenlyDiscretizedFunc meanExpNumDist = cumMFD_FromAllCatalogsFunc_3D.getMeanCurve();
		
		System.out.println(meanExpNumDist);

		
		for(double mag:magArray) {
//			System.out.println("working on M="+mag);
			int magIndex = meanExpNumDist.getClosestXIndex(mag);
			double expNum = meanExpNumDist.getY(magIndex);
//			System.out.println("Exp Num at M="+mag+" is: "+(float)meanExpNumDist.getY(magIndex));
			ArbDiscrEmpiricalDistFunc numDist = cumMFD_FromAllCatalogsFunc_3D.getArbDiscrEmpDistFuncArray()[magIndex].getNormalizedDist();
//			System.out.println(numDist);

			int maxNum = (int)Math.round(numDist.getMaxX());
			EvenlyDiscretizedFunc poissNumDist = new EvenlyDiscretizedFunc(0.,maxNum+1,1.0);
			PoissonDistribution poisDist = new PoissonDistribution(expNum);
			for(int i=0;i<poissNumDist.size();i++) {
				poissNumDist.set(i,poisDist.probability(i));
			}
			numDist.setName("Catalog Num Distribution for M="+mag);
			numDist.setInfo("ExpNum = : "+expNum);
			poissNumDist.setName("Poisson Num Distribution for M="+mag);
			poissNumDist.setInfo("ExpNum = : "+expNum);
			ArrayList<XY_DataSet> plottingFuncsArray = new ArrayList<XY_DataSet>();
			plottingFuncsArray.add(numDist);
			plottingFuncsArray.add(poissNumDist);
			this.quickPlot(plottingFuncsArray,"Num","Probability","M="+mag+"; Duration="+duration);

		}

	}

	
	/**
	 * This divides the catalogs up into subcatalogs of the given duration
	 * @param catalogList
	 * @param duration - yrs
	 * @return
	 */
	public static ArrayList<ObsEqkRupList> getSubcatalogList(ArrayList<ObsEqkRupList> catalogList, double duration) {
		
		int numSubCatPerCat = (int)Math.floor((catalogDuration+0.003)/duration);	// add a day (0.03) to make sure we get the last window
		if(D) System.out.println("numSubCatPerCat = "+numSubCatPerCat);
		
		ArrayList<ObsEqkRupList> eqkRupListList = new ArrayList<ObsEqkRupList>();
		for(ObsEqkRupList catalog : catalogList) {
			long endEpoch = (long)((catalogStartYear-1970)*millisPerYr) + (long)(duration*millisPerYr);
			ObsEqkRupList currEqkList = new ObsEqkRupList();
			eqkRupListList.add(currEqkList);
			for(ObsEqkRupture rup: catalog) {
				if(rup.getOriginTime()<endEpoch)
					currEqkList.add(rup);
				else {
					currEqkList = new ObsEqkRupList();
					eqkRupListList.add(currEqkList);
					endEpoch += (long)(duration*millisPerYr);
				}
			}
		}
		if(D) System.out.println("eqkRupListList.size() = "+eqkRupListList.size()+"; it should be "+numSubCatPerCat*catalogList.size());
		
		return eqkRupListList;
	}
	
	
	
	
	/**
	 * This plots a 500-year cumulative number of events versus times for the least, most, and median catalogs in terms
	 * total number of events (at 500 years).
	 * @param catalogList
	 * @param dirName
	 * @param popupWindow
	 * @param plotTitle
	 */
	public void writeAndOrPlotRateVersusTime(ArrayList<ObsEqkRupList> catalogList, String dirName, boolean popupWindow, String plotTitle) {
		
		double[] totNumArray = new double[catalogList.size()];
		
		ArbitrarilyDiscretizedFunc[] allFuncsArray = new ArbitrarilyDiscretizedFunc[catalogList.size()];

		for(int i=1;i<catalogList.size();i++ ) {
			ArbitrarilyDiscretizedFunc cumNumMgt5 = new ArbitrarilyDiscretizedFunc();
			long firstTime = catalogList.get(i).get(0).getOriginTime();
			int num = 1;
			for(ObsEqkRupture rup : catalogList.get(i)) {
				double yrs = ((double)(rup.getOriginTime()-firstTime))/(double)millisPerYr;
				cumNumMgt5.set(yrs,num);
				num+=1;
			}
			allFuncsArray[i] = cumNumMgt5;
			totNumArray[i] = cumNumMgt5.getY(cumNumMgt5.size()-1);
		}
		
		double median = Quantiles.median().compute(totNumArray);
		double min = Double.MAX_VALUE;
		double max = Double.NEGATIVE_INFINITY;
		int medianIndex=-1, minIndex=-1, maxIndex=-1;
		double minMedianDiff = Double.MAX_VALUE;
		
		for(int i=1;i<catalogList.size();i++ ) {
			double medianDiff = Math.abs(totNumArray[i]-median);
			if(medianDiff < minMedianDiff) {
				minMedianDiff = medianDiff;
				medianIndex=i;
			}
			if(totNumArray[i]<min) {
				min = totNumArray[i];
				minIndex=i;
			}
			if(totNumArray[i]>max) {
				max = totNumArray[i];
				maxIndex=i;
			}
		}		
		
		if(D) {
			double maxMag = 0;
			long ot = 0;
			for(ObsEqkRupture rup : catalogList.get(maxIndex)) {
				if(rup.getMag()>maxMag) {
					maxMag = rup.getMag();
					ot = rup.getOriginTime();
				}
			}
			double yrs = ((double)ot - (catalogStartYear-1970.)*millisPerYr)/millisPerYr;
			System.out.println("maxMag = "+maxMag+" at "+yrs+" yrs");
		}


		
		ArrayList<XY_DataSet> plottingFuncsArray = new ArrayList<XY_DataSet>();
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	
		
		plottingFuncsArray.add(allFuncsArray[medianIndex]);
		plottingFuncsArray.add(allFuncsArray[minIndex]);
		plottingFuncsArray.add(allFuncsArray[maxIndex]);
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		
		String fileNamePrefix = null;
		if(dirName != null)
			fileNamePrefix = dirName+"/cumNumEventsVsTime"+plotTitle;
		String xAxisLabel = "Time (years)";
		String yAxisLabel = "Cumulative Number of Events";
		Range xAxisRange = null;
		Range yAxisRange = null;
		boolean logX = false;
		boolean logY = false;

		PlottingUtils.writeAndOrPlotFuncs(plottingFuncsArray, plotChars, plotTitle, xAxisLabel, yAxisLabel, 
				xAxisRange, yAxisRange, logX, logY, fileNamePrefix, popupWindow);
		
	}

	
	public void quickPlot(ArrayList<XY_DataSet> plottingFuncsArray, String xAxisLabel, String yAxisLabel, String title) {
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	
		
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		
		Range xAxisRange = null;
		Range yAxisRange = null;
		boolean logX = false;
		boolean logY = false;

		PlottingUtils.writeAndOrPlotFuncs(plottingFuncsArray, plotChars, title, xAxisLabel, yAxisLabel, 
				xAxisRange, yAxisRange, logX, logY, null, true);

	}
	
	public static void writeAndOrPlotHazardCurves(ArrayList<UncertainArbDiscDataset[]> dataSetsArray, ArrayList<XY_DataSet> funcsArray, double duration, 
			double saPeriod, String dirName, boolean popupWindow, String plotTitle) {
		
		Color[] colorArray = {Color.BLUE, Color.RED, Color.BLACK, Color.MAGENTA, Color.CYAN};
		int colorIndex = 0;
		
		String imtString = getIMT_String(saPeriod);

		ArrayList<XY_DataSet> plottingFuncsArray = new ArrayList<XY_DataSet>();
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	

		for(UncertainArbDiscDataset[] dataSets:dataSetsArray) {
			plottingFuncsArray.add(dataSets[1]); // for solid line
			plottingFuncsArray.add(dataSets[1]); // for shaded region
			plottingFuncsArray.add(dataSets[0].getLower());
			plottingFuncsArray.add(dataSets[0].getUpper());
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 1f, colorArray[colorIndex]));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, colorArray[colorIndex]));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, colorArray[colorIndex]));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, colorArray[colorIndex]));
			colorIndex += 1;
		}
		
		
		// add other curves
		for(XY_DataSet func:funcsArray) {
			plottingFuncsArray.add(func);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, colorArray[colorIndex]));
			colorIndex += 1;
		}
		
		
		String fileNamePrefix = null;
		if(dirName != null)
			fileNamePrefix = dirName+"/hazardCurve"+"_"+imtString+"_"+plotTitle;
		String xAxisLabel = imtString;
		String yAxisLabel = "Probability (in "+duration+" yr)";
		Range xAxisRange = new Range(1e-2,10);
		Range yAxisRange = new Range(1e-4,1.0);
		boolean logX = true;
		boolean logY = true;

		PlottingUtils.writeAndOrPlotFuncs(plottingFuncsArray, plotChars, plotTitle, xAxisLabel, yAxisLabel, 
				xAxisRange, yAxisRange, logX, logY, fileNamePrefix, popupWindow);

	}
	
	
	

	
	private void tempPlotAllHazardCurves(ArrayList<XY_DataSet> plottingFuncsArray, double duration, String dirName, boolean popupWindow, String plotTitle) {
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	
		
		for(int i=0;i<plottingFuncsArray.size()-1;i++) {
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		}
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
		
		String fileNamePrefix = null;
//		if(dirName != null)
//			fileNamePrefix = dirName+"/hazardCurve"+"_"+imtString+"_"+plotTitle;
		String xAxisLabel = "IMT";
		String yAxisLabel = "Probability (in "+duration+" yr)";
		Range xAxisRange = new Range(Math.log(1e-2),Math.log(10));
		Range yAxisRange = new Range(1e-5,1.0);
		boolean logX = false;
		boolean logY = true;

		PlottingUtils.writeAndOrPlotFuncs(plottingFuncsArray, plotChars, plotTitle, xAxisLabel, yAxisLabel, 
				xAxisRange, yAxisRange, logX, logY, fileNamePrefix, popupWindow);

	}
	
	
	public static UncertainArbDiscDataset get95perConfForMultRuns(ArbDiscrEmpiricalDistFunc_3D arbDiscrEmpiricalDistFunc_3D) {
		EvenlyDiscretizedFunc meanCurve = arbDiscrEmpiricalDistFunc_3D.getMeanCurve();
		EvenlyDiscretizedFunc stdevCurve = arbDiscrEmpiricalDistFunc_3D.getStdDevCurve();
		EvenlyDiscretizedFunc upper95 = stdevCurve.deepClone();
		EvenlyDiscretizedFunc lower95 = stdevCurve.deepClone();
		double numRuns = arbDiscrEmpiricalDistFunc_3D.getArbDiscrEmpDistFuncArray()[0].calcSumOfY_Vals();
		
//		ArbDiscrEmpiricalDistFunc[] funcArray = arbDiscrEmpiricalDistFunc_3D.getArbDiscrEmpDistFuncArray();
//		for(int i=0;i<funcArray.length;i++) {
//			ArbDiscrEmpiricalDistFunc func = funcArray[i];
//			System.out.println("HERE: "+func.size()+"\t"+func.calcSumOfY_Vals());
//		}
		
		double sqrtNum = Math.sqrt(numRuns);
		for(int i=0;i<meanCurve.size();i++) {
			double mean = meanCurve.getY(i);
			double stdom = stdevCurve.getY(i)/sqrtNum;
			upper95.set(i,mean+1.96*stdom);
			lower95.set(i,mean-1.96*stdom);
		}
		return new UncertainArbDiscDataset(meanCurve,lower95,upper95);
	}


	
	/**
	 */
	private static EvenlyDiscretizedFunc computeHazardCurveLnX(ObsEqkRupList obsQkList, Location location, double saPeriod, double forecastDuration, 
			boolean randomIML, ScalarIMR imr) {
		
		// set imr to default values
		imr.setParamDefaults();
		
		EvenlyDiscretizedFunc curveLogXvalues = new EvenlyDiscretizedFunc(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		// make the site object and set values
		Site site = new Site(location);
		for (Parameter<?> param : imr.getSiteParams()) {
			site.addParameter(param);
//			System.out.println(param.getName()+"\t"+param.getValue());
		}
		
		// set the IMT
		ArbitrarilyDiscretizedFunc curveLinearXvalues;
		if(saPeriod == 0) {
			imr.setIntensityMeasure(PGA_Param.NAME);
		}
		else {
			SA_Param saParam = (SA_Param)imr.getParameter(SA_Param.NAME);
			saParam.getPeriodParam().setValue(saPeriod);
			imr.setIntensityMeasure(saParam);
		}
		
		HazardCurveCalculator calc = getHazardCurveCalculator();
		
		ArrayList<EqkRupture> eqkRupList = new ArrayList<EqkRupture>();
		for(ObsEqkRupture rup: obsQkList)
			eqkRupList.add(rup);
		
		if(randomIML)
			calc.getEventSetHazardCurveRandomIML(curveLogXvalues, site, imr, eqkRupList, false);
		else
			calc.getEventSetHazardCurve(curveLogXvalues, site, imr, eqkRupList, false);

		
		return curveLogXvalues;
	}
	
	
	/**
	 * This gets the hazare curve calculator, setting any special parameters (do this once here for consistency)
	 * @param calc
	 * @return
	 */
	private static HazardCurveCalculator getHazardCurveCalculator() {
		HazardCurveCalculator calc = new HazardCurveCalculator();
		calc.setPtSrcDistCorrType(PtSrcDistCorr.Type.NSHMP08);
		calc.setMinMagnitude(5.0);
		return calc;
	}
	
	
	/**
	 */
	private static EvenlyDiscretizedFunc computeExpNumExceedCurveLnX(ObsEqkRupList obsQkList, Location location, 
			double saPeriod, double forecastDuration, ScalarIMR imr) {
		
		// set imr to default values
		imr.setParamDefaults();
		
		EvenlyDiscretizedFunc curveLogXvalues = new EvenlyDiscretizedFunc(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		// make the site object and set values
		Site site = new Site(location);
		for (Parameter<?> param : imr.getSiteParams()) {
			site.addParameter(param);
//			System.out.println(param.getName()+"\t"+param.getValue());
		}
		
		// set the IMT
		ArbitrarilyDiscretizedFunc curveLinearXvalues;
		if(saPeriod == 0) {
			imr.setIntensityMeasure(PGA_Param.NAME);
		}
		else {
			SA_Param saParam = (SA_Param)imr.getParameter(SA_Param.NAME);
			saParam.getPeriodParam().setValue(saPeriod);
			imr.setIntensityMeasure(saParam);
		}
		
		HazardCurveCalculator calc = getHazardCurveCalculator();
		
		ArrayList<EqkRupture> eqkRupList = new ArrayList<EqkRupture>();
		for(ObsEqkRupture rup: obsQkList)
			eqkRupList.add(rup);
		
		calc.getEventSetExpNumExceedCurve(curveLogXvalues, site, imr, eqkRupList, false);

		return curveLogXvalues;
	}
	
	
	/**
	 */
	private EvenlyDiscretizedFunc computeNumExceedCurveRandomIML_LnX(ObsEqkRupList obsQkList, Location location, 
			double saPeriod, double forecastDuration, ScalarIMR imr) {
		
		// set imr to default values
		imr.setParamDefaults();
		
		EvenlyDiscretizedFunc curveLogXvalues = new EvenlyDiscretizedFunc(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		// make the site object and set values
		Site site = new Site(location);
		for (Parameter<?> param : imr.getSiteParams()) {
			site.addParameter(param);
//			System.out.println(param.getName()+"\t"+param.getValue());
		}
		
		// set the IMT
		ArbitrarilyDiscretizedFunc curveLinearXvalues;
		if(saPeriod == 0) {
			imr.setIntensityMeasure(PGA_Param.NAME);
		}
		else {
			SA_Param saParam = (SA_Param)imr.getParameter(SA_Param.NAME);
			saParam.getPeriodParam().setValue(saPeriod);
			imr.setIntensityMeasure(saParam);
		}
		
		HazardCurveCalculator calc = getHazardCurveCalculator();
		
		ArrayList<EqkRupture> eqkRupList = new ArrayList<EqkRupture>();
		for(ObsEqkRupture rup: obsQkList)
			eqkRupList.add(rup);
		
		calc.getEventSetNumExceedCurveRandomIML(curveLogXvalues, site, imr, eqkRupList, false);

		return curveLogXvalues;
	}

	
	

	
	
	/**
	 */
	private void testRandomSamplesFromIMR(EqkRupture rup, Location location, double saPeriod, ScalarIMR imr) {
		
		// set imr to default values
		imr.setParamDefaults();
		imr.getParameter(SigmaTruncTypeParam.NAME).setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_2SIDED);
		imr.getParameter(SigmaTruncLevelParam.NAME).setValue(2.0);
		
		
		// make the site object and set values
		Site site = new Site(location);
		for (Parameter<?> param : imr.getSiteParams()) {
			site.addParameter(param);
			System.out.println(param.getName()+"\t"+param.getValue());
		}
		
		// set the IMT
		ArbitrarilyDiscretizedFunc curveLinearXvalues;
		if(saPeriod == 0) {
			imr.setIntensityMeasure(PGA_Param.NAME);
		}
		else {
			SA_Param saParam = (SA_Param)imr.getParameter(SA_Param.NAME);
			saParam.getPeriodParam().setValue(saPeriod);
			imr.setIntensityMeasure(saParam);
		}
		
		imr.setSite(site);
		imr.setEqkRupture(rup);
		double mean = imr.getMean();
		double std = imr.getStdDev();
		
		HistogramFunction hist = new HistogramFunction(mean-5.*std, mean+5.*std, 200);
		for(int i=0;i<1000000;i++) {
			double rand = imr.getRandomIML();
			if(rand<mean+5*std && rand>mean-5*std)
			hist.add(rand, 1.0);
		}
		
		System.out.println("mean from imr = "+mean+"\nstdDev from imr = "+std);

		hist.setInfo("mean="+(float)hist.computeMean()+"\nstd="+(float)hist.computeStdDev());
		
		ArrayList<XY_DataSet> plottingFuncsArray = new ArrayList<XY_DataSet>();
		plottingFuncsArray.add(hist);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();	
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));

		PlottingUtils.writeAndOrPlotFuncs(plottingFuncsArray, plotChars, "Test", "IML", "Num", 
				null, null, false, false, null, true);
	}

	
	
	
	private ArbitrarilyDiscretizedFunc computeHazardCurveFromERF(FaultSystemSolutionERF erf, Location location, 
			double saPeriod, double forecastDuration, ScalarIMR imr) {
		

		// set imr to default values
		imr.setParamDefaults();
		
		EvenlyDiscretizedFunc curveLogXvalues = new EvenlyDiscretizedFunc(hazCurveLnMin,hazCurveNum,hazCurveDelta);
		
		// make the site object and set values
		Site site = new Site(location);
		for (Parameter<?> param : imr.getSiteParams()) {
			site.addParameter(param);
//			System.out.println(param.getName()+"\t"+param.getValue());
		}
		
		// set the IMT
		if(saPeriod == 0) {
			imr.setIntensityMeasure(PGA_Param.NAME);
		}
		else {
			SA_Param saParam = (SA_Param)imr.getParameter(SA_Param.NAME);
			saParam.getPeriodParam().setValue(saPeriod);
			imr.setIntensityMeasure(saParam);
		}
		
		HazardCurveCalculator calc = getHazardCurveCalculator();
		
		calc.getHazardCurve(curveLogXvalues, site, imr, erf); // result is put into curveLogXvalues
		
		ArbitrarilyDiscretizedFunc curveLinearXvalues = new ArbitrarilyDiscretizedFunc();
		for(int i=0;i<curveLogXvalues.size();i++)
			curveLinearXvalues.set(Math.exp(curveLogXvalues.getX(i)),curveLogXvalues.getY(i));
		
		curveLinearXvalues.setName("computeHazardCurveFromERF");
		return curveLinearXvalues;
	}
	
	
	

	
	public  FaultSystemSolutionERF getTimeIndERF_Instance(double duration) {
		FaultSystemSolution sol=null;
		try {
			sol = FaultSystemIO.loadSol(new File(fssFileName));
		} catch (Exception e) {
			e.printStackTrace();
		}
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		// set parameters
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
		erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.setParameter(AleatoryMagAreaStdDevParam.NAME, 0.0);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();
		
		// write out parameter values
		if(D) {
			ParameterList paramList = erf.getAdjustableParameterList();
			for(int i=0;i<paramList.size(); i++) {
				Parameter<?> param = paramList.getByIndex(i);
				System.out.println(param.getName()+"\t"+param.getValue());
			}			
		}
		if(D) System.out.println("NumFaultSystemSources = "+erf.getNumFaultSystemSources());
		
		double minMag = erf.getSource(erf.getNumSources()-1).getRupture(0).getMag();
		if(D) System.out.println("minMag = "+minMag);

		return erf;

	}
	
	
	public ArrayList<ObsEqkRupList> getCatalogs() {
		return catalogList;
	}
	
	public ArrayList<ObsEqkRupList> getCatalogsDeclustered() {
		return catalogDeclusteredList;
	}
	
	
	/**
	 * Values here were obtained from https://earthquake.usgs.gov/hazards/interactive/ with the following setting:
	 * 
	 *     Conterminous U.S. 2014 (v4.0.x)
	 *     PGA
	 *     Lat=34.05
	 *     Lon = -118.25
	 *     Site Class = 760 m/s
	 * 
	 */
	private ArbitrarilyDiscretizedFunc getUSGS_LosAngelesPGA_2in50_HazardCurve() {

		ArbitrarilyDiscretizedFunc rateCurve = new ArbitrarilyDiscretizedFunc();
		rateCurve.set(0.005,0.4432);
		rateCurve.set(0.007,0.34746);
		rateCurve.set(0.0098,0.26823);
		rateCurve.set(0.0137,0.20393);
		rateCurve.set(0.0192,0.15156);
		rateCurve.set(0.0269,0.10967);
		rateCurve.set(0.0376,0.077065);
		rateCurve.set(0.0527,0.052227);
		rateCurve.set(0.0738,0.034316);
		rateCurve.set(0.103,0.021958);
		rateCurve.set(0.145,0.013427);
		rateCurve.set(0.203,0.007977);
		rateCurve.set(0.284,0.0045447);
		rateCurve.set(0.397,0.00244);
		rateCurve.set(0.556,0.0011921);
		rateCurve.set(0.778,0.00051457);
		rateCurve.set(1.09,0.00018778);
		rateCurve.set(1.52,5.6305e-05);
		rateCurve.set(2.2,1.0659e-05);
		rateCurve.set(3.3,1.746e-06);

		ArbitrarilyDiscretizedFunc hazCurve = new ArbitrarilyDiscretizedFunc();
		for(int i=0;i<rateCurve.size();i++)
			hazCurve.set(rateCurve.getX(i),1.0 - Math.exp(-50*rateCurve.getY(i)));
		
		hazCurve.setName("USGS_LosAngelesPGA_2in50_HazardCurve");
			
		return hazCurve;
	}


	private ArrayList<ObsEqkRupList> makeRandomCatalogListFromERF(FaultSystemSolutionERF erf) {
		ArrayList<ObsEqkRupList> catalogList = new ArrayList<ObsEqkRupList>();
		erf.getTimeSpan().setDuration(catalogDuration);
		erf.updateForecast();
		int numCatalogs = 1000;
		for(int i=0;i<numCatalogs; i++) {
			ArrayList<EqkRupture> rupList = erf.drawRandomEventSet();
			ObsEqkRupList obsRupList = new ObsEqkRupList();
			for(EqkRupture rup:rupList) {
				if(rup.getMag()<5)
					continue;
				ObsEqkRupture obsRup = new ObsEqkRupture();
				obsRup.setAveRake(rup.getAveRake());
				obsRup.setMag(rup.getMag());
				obsRup.setRuptureSurface(rup.getRuptureSurface());
				double randYrs = (catalogStartYear-1970.) + Math.random()*catalogDuration;
				long ot = (long)(randYrs*this.millisPerYr);
				obsRup.setOriginTime(ot);
				obsRupList.add(obsRup);
			}
			obsRupList.sortByOriginTime();
			catalogList.add(obsRupList);
		}
				
				return catalogList;
	}
	
	private static void demoLoadZippedCSVs(File file) throws IOException {
		ZipFile zip = new ZipFile(file);
		
		Enumeration<? extends ZipEntry> entries = zip.entries();
		
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			String name = entry.getName();
			if (!name.endsWith(".csv"))
				continue;
			System.out.println("Loading "+entry);
			String dirName = name.substring(0, name.indexOf('/'));
			String fileName = name.substring(name.indexOf('/')+1);
			
			// parse the name for node/location
			String[] split = dirName.split("_");
			int node = Integer.parseInt(split[1]);
			double lat = Double.parseDouble(split[2]);
			double lon = Double.parseDouble(split[2]);
			Location loc = new Location(lat, lon);
			
			String imt;
			double period;
			if (fileName.startsWith("sa_")) {
				imt = SA_Param.NAME;
				String periodStr = fileName.substring(3, fileName.indexOf(".csv"));
				period = Double.parseDouble(periodStr);
			} else {
				imt = PGA_Param.NAME;
				period = 0d;
			}
			
			System.out.println(fileName);
			System.out.println("Loading "+name+": node="+node+", loc="+loc+", "+imt+" ("+(float)period+" s)");
			CSVFile<String> csv = CSVFile.readStream(zip.getInputStream(entry), true);
			
			int col = 1;
			ArbitrarilyDiscretizedFunc fullMean = loadFuncFromCSV(csv, 0, col++);
			UncertainArbDiscDataset fullMinMax = new UncertainArbDiscDataset(
					fullMean, loadFuncFromCSV(csv, 0, col++), loadFuncFromCSV(csv, 0, col++));
			UncertainArbDiscDataset fullConf95 = new UncertainArbDiscDataset(
					fullMean, loadFuncFromCSV(csv, 0, col++), loadFuncFromCSV(csv, 0, col++));
			ArbitrarilyDiscretizedFunc poisson = loadFuncFromCSV(csv, 0, col++);
			ArbitrarilyDiscretizedFunc declusteredMean = loadFuncFromCSV(csv, 0, col++);
			UncertainArbDiscDataset declusteredMinMax = new UncertainArbDiscDataset(
					declusteredMean, loadFuncFromCSV(csv, 0, col++), loadFuncFromCSV(csv, 0, col++));
			UncertainArbDiscDataset declusteredConf95 = new UncertainArbDiscDataset(
					declusteredMean, loadFuncFromCSV(csv, 0, col++), loadFuncFromCSV(csv, 0, col++));
			ArbitrarilyDiscretizedFunc randomMean = loadFuncFromCSV(csv, 0, col++);
			UncertainArbDiscDataset randomMinMax = new UncertainArbDiscDataset(
					randomMean, loadFuncFromCSV(csv, 0, col++), loadFuncFromCSV(csv, 0, col++));
			UncertainArbDiscDataset randomConf95 = new UncertainArbDiscDataset(
					randomMean, loadFuncFromCSV(csv, 0, col++), loadFuncFromCSV(csv, 0, col++));
			if (node == 0)
				System.out.println(fullMinMax);
			Preconditions.checkState(col == csv.getNumCols());
		}
		
		zip.close();
	}
	
	private static ArbitrarilyDiscretizedFunc loadFuncFromCSV(CSVFile<String> csv, int xCol, int yCol) {
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		for (int row=1; row<csv.getNumRows(); row++)
			func.set(csv.getDouble(row, xCol), csv.getDouble(row, yCol));
		return func;
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		demoLoadZippedCSVs(new File("/home/kevin/OpenSHA/UCERF3/etas/etas_decluster/"
				+ "2020_12_11-decluster-full-td-kCOV1.5-scale1.14-spacing1/results.zip"));
		System.exit(0);
		// FOR KEVIN **********************
		
		// loop over sites in RELM gridded region
		Location loc = new Location(34.05,-118.25);
		// loop over saPeriods of 0, 0.2, 1, 5
		double saPeriod = 0; // 0 = PGA
		
		ScalarIMR imr = AttenRelRef.CB_2014.instance(null);
		
		double duration = 50;
		
		Random random = new Random();
		
		ArrayList<ObsEqkRupList> catalogsList = U3ETAS_SimulationAnalysis.loadCatalogs(new File(fssFileName), new File(catalogsFileName));
		UncertainArbDiscDataset[] datasetsArray1 = U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(catalogsList, loc, duration, saPeriod, false, imr);
		ArbitrarilyDiscretizedFunc poissonCurve = U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogsPoisson(catalogsList, loc, duration, saPeriod, imr);
		ArrayList<ObsEqkRupList> declusteredCatalogsList = U3ETAS_SimulationAnalysis.getGK_DeclusteredCatalog(catalogsList);
		UncertainArbDiscDataset[] datasetsArray2 = U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(declusteredCatalogsList, loc, duration, saPeriod, false, imr);
		ArrayList<ObsEqkRupList> catalogsRandmizedList = U3ETAS_SimulationAnalysis.getRandomizedCatalogs(catalogsList, random);
		UncertainArbDiscDataset[] datasetsArray3 = U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(catalogsRandmizedList, loc, duration, saPeriod, false, imr);
		
		// write out contents of poissonCurve, datasetsArray1, datasetsArray2, and datasetsArray3,
		
		// END FOR KEVIN **********************
		
		
		
//		// TEST PLOT THE ABOVE:
		ArrayList<UncertainArbDiscDataset[]> dataSetsArray = new ArrayList<UncertainArbDiscDataset[]>();
		ArrayList<XY_DataSet> funcsArray = new ArrayList<XY_DataSet>();
		
		String locName = "Los Angeles";
		
		dataSetsArray.add(datasetsArray1);
		dataSetsArray.add(datasetsArray2);
		dataSetsArray.add(datasetsArray3);
		funcsArray.add(poissonCurve);
		U3ETAS_SimulationAnalysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);

	
		
//		U3ETAS_SimulationAnalysis analysis = new U3ETAS_SimulationAnalysis();
//		
//		
//		funcsArray.add(analysis.computeHazardCurvesFromCatalogsPoisson(analysis.getCatalogs(), loc, duration, saPeriod, imr)); // Magenta
//		dataSetsArray.add(U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(analysis.getCatalogs(), loc, duration, saPeriod, false, imr)); // Blue
//		dataSetsArray.add(U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(analysis.getCatalogsDeclustered(), loc, duration, saPeriod, false, imr));	// Red
//		dataSetsArray.add(U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(analysis.getRandomizedCatalogs(), loc, duration, saPeriod, false, imr)); // Black
//		U3ETAS_SimulationAnalysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);

		
//		analysis.doMFDs();
		
//		// THIS COMPARES HAZARD CURVES USING THE TI ERF WITH RANDOM CATALOGS FROM THE ERF (NOTE THE HARD CODING COMMENT)
//		// THE MATCH IS PERFECT
//		FaultSystemSolutionERF erf = analysis.getTimeIndERF_Instance(duration);
//		funcsArray.add(analysis.computeHazardCurveFromERF(erf, loc, saPeriod, duration, imr)); // HAD TO SKIP M≤5 EVENTS IN THE HAZARD CURVE CALCULATOR (HARD CODED) FOR THIS TO WORK
//		System.out.println("starting Rand Cats");
//		ArrayList<ObsEqkRupList> randCats = analysis.makeRandomCatalogListFromERF(erf);
//		System.out.println("done with Rand Cats");
//		dataSetsArray.add(analysis.computeHazardCurvesFromCatalogs(randCats, loc, duration, saPeriod, false, imr));
//		funcsArray.add(analysis.computeHazardCurvesFromCatalogsPoisson(randCats, loc, duration, saPeriod, imr));
//		analysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);
		
//		// THIS COMPARES THE USGS LA HAZARD CURVE TO THAT COMPUTED WITH THE ERF FOR 4 DIFF IMRs
//		// MATCH IS GOOD AT 2 IN 50 ; 10% OFF AT LOWER AND HIGHES END
//		// NO ADDED GMM EPISTEMIC UNCERTAINTY AND ONE SMOOTHED SIES BRANCH HERE SO SOME DIFFS EXPECTED
//		FaultSystemSolutionERF erf = analysis.getTimeIndERF_Instance(duration);
//		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, true);
//		erf.updateForecast();
//		imr = AttenRelRef.CB_2014.instance(null);
//		funcsArray.add(analysis.computeHazardCurveFromERF(erf, loc, saPeriod, duration, imr)); // HAD TO SKIP M≤5 EVENTS IN THE HAZARD CURVE CALCULATOR (HARD CODED) FOR THIS TO WORK
//		imr = AttenRelRef.ASK_2014.instance(null);
//		funcsArray.add(analysis.computeHazardCurveFromERF(erf, loc, saPeriod, duration, imr)); // HAD TO SKIP M≤5 EVENTS IN THE HAZARD CURVE CALCULATOR (HARD CODED) FOR THIS TO WORK
//		imr = AttenRelRef.BSSA_2014.instance(null);
//		funcsArray.add(analysis.computeHazardCurveFromERF(erf, loc, saPeriod, duration, imr)); // HAD TO SKIP M≤5 EVENTS IN THE HAZARD CURVE CALCULATOR (HARD CODED) FOR THIS TO WORK
//		imr = AttenRelRef.CY_2014.instance(null);
//		funcsArray.add(analysis.computeHazardCurveFromERF(erf, loc, saPeriod, duration, imr)); // HAD TO SKIP M≤5 EVENTS IN THE HAZARD CURVE CALCULATOR (HARD CODED) FOR THIS TO WORK
//		funcsArray.add(analysis.getUSGS_LosAngelesPGA_2in50_HazardCurve());
//		analysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);

		
//		// THIS COMPARES ERF HAZARD CURVE TO U3ETAS RANDOMIZED CURVE; DIFFERENCE IS 16% AT 2 IN 50, WHICH SEEMS HIGH
//		// BUT IS WITHIN UNCERTAINTIES AT LA FOR U3-TI SUPPLEMENTAL DATA
//		// MFD COMPARISON SHOWS THE PROBLEM THAT U3ETAS IS UNDERPREDICTING (by 26%) EVENTS AROUND M≥7, WHICH IS ABOUT THE SAME
//		// AMOUNT THE BSSA PAPER SHOWS.  NEED TO BUMP UP THE totRateScaleFactor BY 31% (1.14*1.31=1.5) TO 
//		// BALANCE MFD DISCREPENCIES. NoERT CASE SHOWS LESS, BUT STILL A DISCREPANCY (14% ON MFD; 12% ON 2IN50).
//		FaultSystemSolutionERF erf = analysis.getTimeIndERF_Instance(duration);
//		funcsArray.add(analysis.computeHazardCurveFromERF(erf, loc, saPeriod, duration, imr));
//		funcsArray.add(analysis.computeHazardCurvesFromCatalogsPoisson(analysis.getCatalogs(), loc, duration, saPeriod, imr));
//		funcsArray.add(analysis.getUSGS_LosAngelesPGA_2in50_HazardCurve());
//		analysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);
//		// COMPARE MFDS
//		ArrayList<XY_DataSet> funcsArray2 = new ArrayList<XY_DataSet>();
//		SummedMagFreqDist erfMFD = ERF_Calculator.getTotalMFD_ForERF(erf, minMag, 8.95, numMag, true);
//		funcsArray2.add(erfMFD);
//		funcsArray2.add(erfMFD.getCumRateDistWithOffset());
//		IncrementalMagFreqDist catMFD = analysis.makeMFD(analysis.getCatalogs());
//		funcsArray2.add(catMFD);
//		funcsArray2.add(catMFD.getCumRateDistWithOffset());
//		double aveLnRatio = 0;
//		int num=0;
//		for(int i=0;i<erfMFD.size();i++)
//			if(catMFD.getY(i) > 0.0) {
//				aveLnRatio += Math.log((erfMFD.getY(i)/catMFD.getY(i)));
//				num+=1;
//			}
//		aveLnRatio /=num;
//		System.out.println("aveRatio="+Math.exp(aveLnRatio));
//		analysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray2, duration, saPeriod, null, true, locName);


//		// THIS LOOKS AT NUM EXCEEDANCE DISTRIBUTIONS AT THE SPECIFIED IMLS USING RANDOM IML SAMPLES
//		analysis.computeNumHazardExceedDistRandomIML(analysis.getCatalogs(), loc, duration, saPeriod, new double[]{0.02,0.2,0.7,2.0}, imr);
//		analysis.computeNumHazardExceedDistRandomIML(analysis.getRandomizedCatalogs(), loc, duration, saPeriod, new double[]{0.02,0.2,0.7,2.0}, imr);

//		// THIS LOOKS AT NUM EXCEEDANCE DISTRIBUTIONS AT THE SPECIFIED IMLS
//		analysis.computeNumHazardExceedDist(analysis.getCatalogs(), loc, duration, saPeriod, new double[]{0.02,0.2,0.7,2.0}, imr);
//		analysis.computeNumHazardExceedDist(analysis.getRandomizedCatalogs(), loc, duration, saPeriod, new double[]{0.02,0.2,0.7,2.0}, imr);

		
		
		
//		// THIS LOOKS AT THE NUM DISTRIBUTION OF EVENTS ABOVE VARIOUS MAG THRESHOLDS
//		// THINGS LOOK POISSONIAN FOR M≥7 ISH
//		duration = 50;
//		analysis.computeNumEventDistribution(analysis.getCatalogs(), duration, new double[]{5.0,6.0, 7.0, 8.0});
//		analysis.computeNumEventDistribution(analysis.getCatalogsDeclustered(), duration, new double[]{5.0,6.0, 7.0, 8.0});
//		analysis.computeNumEventDistribution(analysis.getRandomizedCatalogs(), duration, new double[]{5.0,6.0, 7.0, 8.0});
		
//		// THIS PLOTS CUMULATIVE RATES VERSUS TIME
//		analysis.writeAndOrPlotRateVersusTime(analysis.getCatalogs(), null, true, "Test U3ETAS");
//		analysis.writeAndOrPlotRateVersusTime(analysis.getRandomizedCatalogs(), null, true, "Test Poisson");
		
//		// THIS TESTS THAT CUTTING SUMULATIONS IN HALF PRODUCES CONSISENT RESULTS
//		// cut catalogs in half:
//		ArrayList<ObsEqkRupList> allCatalogs = analysis.getCatalogs();
//		ArrayList<ObsEqkRupList> firstHalfCats = new ArrayList<ObsEqkRupList> ();
//		ArrayList<ObsEqkRupList> secondHalfCats = new ArrayList<ObsEqkRupList> ();
//		for(int i=0;i<500;i++) {
//			firstHalfCats.add(allCatalogs.get(i));
//			secondHalfCats.add(allCatalogs.get(i+500));
//		}
//		dataSetsArray.add(analysis.computeHazardCurvesFromCatalogs(firstHalfCats, loc, duration, saPeriod,false, imr));
//		dataSetsArray.add(analysis.computeHazardCurvesFromCatalogs(secondHalfCats, loc, duration, saPeriod,false, imr));
//		U3ETAS_SimulationAnalysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);
		
//		// THIS TESTS THAT A RANDOMIZED CATALOG PRODUCES THE SAME CURVE AS THAT FROM A POISSON MODEL USING THE AVERAGE NUMBER OF EXCEEDANCES
//		funcsArray.add(analysis.computeHazardCurvesFromCatalogsPoisson(analysis.getCatalogs(), loc, duration, saPeriod, imr));
//		dataSetsArray.add(U3ETAS_SimulationAnalysis.computeHazardCurvesFromCatalogs(analysis.getRandomizedCatalogs(), loc, duration, saPeriod, false, imr));	
//		U3ETAS_SimulationAnalysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);
		
//		// THIS TESTS THAT RANDOM IML SAMPLES PRODUCE THE SAME RESULT
//		boolean randIML = false;
//		dataSetsArray.add(analysis.computeHazardCurvesFromCatalogs(analysis.getCatalogs(), loc, duration, saPeriod, randIML, imr));
//		randIML = true;
//		dataSetsArray.add(analysis.computeHazardCurvesFromCatalogs(analysis.getCatalogs(), loc, duration, saPeriod, randIML, imr));
//		analysis.writeAndOrPlotHazardCurves(dataSetsArray, funcsArray, duration, saPeriod, null, true, locName);
		
		// THIS TESTS THAT RANDOM IMLs FROM THE IMR REPRODUCE THE DISTRIBUTION, INCLUDING TRUNCATION
//		analysis.testRandomSamplesFromIMR(analysis.getCatalogs().get(0).get(0), loc, saPeriod, imr);


	}

}
