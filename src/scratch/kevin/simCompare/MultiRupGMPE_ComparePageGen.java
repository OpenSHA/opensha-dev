package scratch.kevin.simCompare;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jfree.data.Range;
import org.opensha.commons.calc.GaussianDistCalc;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;

import scratch.kevin.MarkdownUtils;
import scratch.kevin.MarkdownUtils.TableBuilder;
import scratch.kevin.bbp.SpectraPlotter;
import scratch.kevin.simulators.hazard.HazardMapComparePlotter;

public abstract class MultiRupGMPE_ComparePageGen<E> {
	
	private SimulationRotDProvider<E> simProv;
	private String simName;
	private List<Site> sites;
	private boolean distJB;
	private double cutoffDist;
	private double minMag;
	private double maxMag;
	
	private List<Range> magRanges;
	private List<RuptureComparisonFilter<E>> magFilters;
	private List<String> magLabels;
	private List<String> magFileLabels;
	private List<Range> distRanges;
	private List<RuptureComparisonFilter<E>> distFilters;
	private List<String> distLabels;
	private List<String> distFileLabels;
	
	private SimulationHazardCurveCalc<E> simCurveCalc;
	
	private Table<E, Site, Map<Integer, Double>> rupSiteAzMap;
	
	protected ExecutorService exec;
	
	private Map<AttenRelRef, LinkedList<ScalarIMR>> gmpesInstancesCache;
	
	protected void init(SimulationRotDProvider<E> simProv, List<Site> sites, boolean distJB, double cutoffDist,
			double minMag, double maxMag) {
		this.simProv = simProv;
		this.simName = simProv.getName();
		this.sites = sites;
		this.distJB = distJB;
		this.cutoffDist = cutoffDist;
		this.minMag = minMag;
		this.maxMag = maxMag;
		
		initBins();
		
		gmpesInstancesCache = new HashMap<>();
		rupSiteAzMap = HashBasedTable.create();
		
		exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}
	
	static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	
	protected String getDistDescription() {
		if (distJB)
			return "Joyner-Boore distance (**rJB**), the shortest horizontal distance from a site to the "
					+ "surface projection of the rupture surface";
		return "rupture surface distance (**rRup**), the shortest 3-d distance from a site to the "
					+ "rupture surface";
	}
	
	protected String getDistShortName() {
		if (distJB)
			return "rJB";
		return "rRup";
	}
	
	private void initBins() {
		magRanges = new ArrayList<>();
		if (minMag < 6d) {
			magRanges.add(new Range(minMag, 6d));
			magRanges.add(new Range(6d, 6.5d));
			magRanges.add(new Range(6.5d, 7d));
		} else if (minMag < 6.5d) {
			magRanges.add(new Range(minMag, 6.5d));
			magRanges.add(new Range(6.5d, 7d));
		} else if (minMag < 7d) {
			magRanges.add(new Range(minMag, 7d));
		}
		magRanges.add(new Range(7d, 7.5d));
		magRanges.add(new Range(7.5, 8d));
		if (maxMag > 8d) {
			if (maxMag < 8.5)
				magRanges.add(new Range(8d, 8.5));
			else
				magRanges.add(new Range(8d, 9));
		}
		magFilters = new ArrayList<>();
		for (Range magRange : magRanges)
			magFilters.add(new RuptureComparisonFilter.MagFilter<>(magRange.getLowerBound(), magRange.getUpperBound()));
		magLabels = new ArrayList<>();
		for (Range magRange : magRanges)
			magLabels.add(optionalDigitDF.format(magRange.getLowerBound())+" < Mw < "
					+optionalDigitDF.format(magRange.getUpperBound()));
		magFileLabels = new ArrayList<>();
		for (Range magRange : magRanges)
			magFileLabels.add("mag_"+optionalDigitDF.format(magRange.getLowerBound())+"_"
					+optionalDigitDF.format(magRange.getUpperBound()));
		
		distRanges = new ArrayList<>();
		// 0-10, 10-20, 20-40, 40-80, 80-160
		distRanges.add(new Range(0d, 10d));
		distRanges.add(new Range(10d, 20d));
		distRanges.add(new Range(20d, 40d));
		distRanges.add(new Range(40d, 80d));
		distRanges.add(new Range(80d, 160d));
		distRanges.add(new Range(160d, cutoffDist));
		distFilters = new ArrayList<>();
		for (Range distRange : distRanges)
			if (distJB)
				distFilters.add(new RuptureComparisonFilter.DistJBFilter<>(distRange.getLowerBound(),
						distRange.getUpperBound()));
			else
				distFilters.add(new RuptureComparisonFilter.DistRupFilter<>(distRange.getLowerBound(),
						distRange.getUpperBound()));
		distLabels = new ArrayList<>();
		String distShortName = getDistShortName();
		for (Range distRange : distRanges)
			distLabels.add(optionalDigitDF.format(distRange.getLowerBound())+" km < "+distShortName+" < "
					+optionalDigitDF.format(distRange.getUpperBound())+" km");
		distFileLabels = new ArrayList<>();
		for (Range distRange : distRanges)
			distFileLabels.add("dist_"+optionalDigitDF.format(distRange.getLowerBound())+"_"
					+optionalDigitDF.format(distRange.getUpperBound()));
	}
	
	public boolean plotScatter(Collection<? extends RuptureComparison<E>> eventComps, Collection<Site> sites, double period,
			AttenRelRef gmpe, RuptureComparisonFilter<E> filter, List<String> binDescriptions, File outputDir, String prefix)
					throws IOException {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		
		int numComputed = 0;
		int numFiltered = 0;
		
		for (Site site : sites) {
			for (RuptureComparison<E> comp : eventComps) {
				if (!comp.isComputed(site, period))
					continue;
				numComputed++;
//					System.out.println("Not computed? "+site.getName()+" "+((RSQSimEvent)comp.getRupture()).getID());
//				System.out.println("Scatter for "+comp.getMagnitude()+" "+site.getName()+" "+comp.getDistanceJB(site));
				if ((filter != null && !filter.matches(comp, site))) {
					numFiltered++;
					continue;
				}
				double gmpeVal = Math.exp(comp.getLogMean(site, period));
				for (DiscretizedFunc spectra : simProv.getRotD50s(site, comp.getRupture())) {
					double simVal = spectra.getY(period);
					xy.set(gmpeVal, simVal);
				}
			}
		}
		System.out.println("XY size: "+xy.size());
		System.out.println(numFiltered+"/"+numComputed+" filtered out");
		if (xy.size() == 0)
			return false;
		
		String title = gmpe.getShortName()+" Comparison Scatter";
		String xAxisLabel = gmpe.getShortName()+" "+(float)period+" s SA (g)";
		String yAxisLabel = simName+" "+(float)period+" s SA (g)";
		
		GroundMotionScatterPlot.PLOT_WIDTH = 600;
		GroundMotionScatterPlot.WRITE_PDF = false;
		GroundMotionScatterPlot.plot(xy, xAxisLabel, yAxisLabel, binDescriptions, title, outputDir, prefix);
		return true;
	}
	
	private static int max_table_fig_columns = 3;
	
	private static int[] hazard_curve_rps = { 1000, 2500, 10000 };
	private static double[] gmpe_truncs = { 0d, 3d, 2d, 1d };
	private static PlotLineType[] gmpe_trunc_line_types = { PlotLineType.SOLID, PlotLineType.DASHED,
			PlotLineType.DOTTED, PlotLineType.DOTTED_AND_DASHED };
	
	public List<File> plotHazardCurves(List<? extends RuptureComparison<E>> comps, List<Site> sites, double period,
			AttenRelRef gmpeRef, File outputDir) throws IOException {
		List<Future<File>> futures = new ArrayList<>();
		
		for (Site site : sites)
			futures.add(exec.submit(new CurveCalcCallable(comps, site, period, gmpeRef, outputDir)));
		
		List<File> files = new ArrayList<>();
		
		for (Future<File> future : futures) {
			try {
				files.add(future.get());
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		
		return files;
	}
	
	private class CurveCalcCallable implements Callable<File> {
		private List<? extends RuptureComparison<E>> comps;
		private Site site;
		private double period;
		private AttenRelRef gmpeRef;
		private File outputDir;

		public CurveCalcCallable(List<? extends RuptureComparison<E>> comps, Site site, double period,
				AttenRelRef gmpeRef, File outputDir) {
			this.comps = comps;
			this.site = site;
			this.period = period;
			this.gmpeRef = gmpeRef;
			this.outputDir = outputDir;
		}

		@Override
		public File call() throws Exception {
			return plotHazardCurve(comps, site, period, gmpeRef, outputDir);
		}
		
	}
	
	protected ScalarIMR checkOutGMPE(AttenRelRef gmpeRef) {
		synchronized (gmpesInstancesCache) {
			LinkedList<ScalarIMR> gmpes = gmpesInstancesCache.get(gmpeRef);
			if (gmpes == null) {
				gmpes = new LinkedList<>();
				gmpesInstancesCache.put(gmpeRef, gmpes);
			}
			if (!gmpes.isEmpty())
				return gmpes.pop();
		}
		ScalarIMR gmpe = gmpeRef.instance(null);
		gmpe.setParamDefaults();
		gmpe.setIntensityMeasure(SA_Param.NAME);
		return gmpe;
	}
	
	protected void checkInGMPE(AttenRelRef gmpeRef, ScalarIMR gmpe) {
		synchronized (gmpesInstancesCache) {
			gmpesInstancesCache.get(gmpeRef).push(gmpe);
		}
	}
	
	@SuppressWarnings("unchecked")
	public File plotHazardCurve(List<? extends RuptureComparison<E>> comps, Site site, double period, AttenRelRef gmpeRef, File outputDir)
			throws IOException {
		String xAxisLabel = (float)period+"s SA (g)";
		String yAxisLabel = "Annual Probability";
		double curveDuration = 1d;
		
		if (simCurveCalc == null)
			simCurveCalc = new SimulationHazardCurveCalc<>(simProv);
		
		System.out.println("Calculating simulation curve for "+site.getName()+", "+xAxisLabel);
		DiscretizedFunc simCurve = simCurveCalc.calc(site, period, curveDuration);
		
		// now calculate GMPE
		System.out.println("Calculating "+gmpeRef.getShortName()+" curve for "+site.getName()+", "+xAxisLabel);
		DiscretizedFunc[] gmpeCurves = new DiscretizedFunc[gmpe_truncs.length];
		for (int t=0; t<gmpeCurves.length; t++) {
			gmpeCurves[t] = simCurve.deepClone();
			// init to 1, non-exceedance curves
			for (int i=0; i<gmpeCurves[t].size(); i++)
				gmpeCurves[t].set(i, 1d);
		}
		DiscretizedFunc gmpeMeanCurve = simCurve.deepClone();
		for (int i=0; i<gmpeMeanCurve.size(); i++)
			gmpeMeanCurve.set(i, 0);
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : gmpeCurves[0])
			logXVals.set(Math.log(pt.getX()), 1d);
		logXVals = new LightFixedXFunc(logXVals);
		for (RuptureComparison<E> comp : new RuptureComparisonFilter.SiteFilter<E>().getMatches(comps, site)) {
			double rupRate = comp.getAnnualRate();
			double rupProb = 1d - Math.exp(-rupRate*curveDuration);
			
			double mean = comp.getLogMean(site, period);
			double stdDev = comp.getStdDev(site, period);
			
			for (int i=0; i<gmpe_truncs.length; i++) {
				double trunc = gmpe_truncs[i];
				for(int k=0; k<logXVals.size(); k++) {
					double stRndVar = (logXVals.getX(k) - mean) / stdDev;
					double exceedProb;
					if (trunc > 0)
						exceedProb = GaussianDistCalc.getExceedProb(stRndVar, 1, trunc);
					else
						exceedProb = GaussianDistCalc.getExceedProb(stRndVar);
					gmpeCurves[i].set(k, gmpeCurves[i].getY(k)*Math.pow(1d-rupProb, exceedProb));
				}
			}
			
			Preconditions.checkState(Double.isFinite(mean));
			for(int k=0; k<logXVals.size(); k++)
				if (mean >= logXVals.getX(k))
					gmpeMeanCurve.set(k, gmpeMeanCurve.getY(k) + rupRate);
		}
		// convert to exceedance probabilities
		for (DiscretizedFunc gmpeCurve : gmpeCurves)
			for (int i=0; i<gmpeCurve.size(); i++)
				gmpeCurve.set(i, 1d-gmpeCurve.getY(i));
		// now mean only curve -> probabilities
		for (int i=0; i<gmpeMeanCurve.size(); i++) {
			double rate = gmpeMeanCurve.getY(i);
			double prob = 1d - Math.exp(-rate*curveDuration);
			gmpeMeanCurve.set(i, prob);
		}
		
		// now plot
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Range xRange = new Range(1e-3, 1e1);
		Range yRange = new Range(1e-8, 1e0);
		
		for (int i=0; i<gmpeCurves.length; i++) {
			DiscretizedFunc gmpeCurve = gmpeCurves[i];
			String name;
			float thickness;
			if (gmpe_truncs[i] == 0) {
				name = gmpeRef.getShortName();
				thickness = 3f;
			} else {
				name = optionalDigitDF.format(gmpe_truncs[i])+" σ";
				thickness = 2f;
			}
			gmpeCurve.setName(name);
			funcs.add(gmpeCurve);
			chars.add(new PlotCurveCharacterstics(gmpe_trunc_line_types[i % gmpe_trunc_line_types.length], thickness, Color.BLUE));
		}
		
		gmpeMeanCurve.setName("Mean Only");
		funcs.add(gmpeMeanCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GREEN.darker()));
		
		simCurve.setName(simName);
		funcs.add(simCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		
		if (hazard_curve_rps != null && hazard_curve_rps.length > 0) {
			CPT rpCPT = HazardMapComparePlotter.getRPlogCPT(hazard_curve_rps);
			for (int rp : hazard_curve_rps) {
				Color color = rpCPT.getColor((float)Math.log10(rp));
				double probLevel = 1d/(double)rp;
				DiscretizedFunc probLine = new ArbitrarilyDiscretizedFunc();
				probLine.set(xRange.getLowerBound(), probLevel);
				probLine.set(xRange.getUpperBound(), probLevel);
				probLine.setName(rp+"yr");
				funcs.add(probLine);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, color));
			}
		}
		
		String siteName = site.getName();

		PlotSpec spec = new PlotSpec(funcs, chars, siteName+" Hazard Curves", xAxisLabel, yAxisLabel);
		spec.setLegendVisible(true);
		
		PlotPreferences plotPrefs = PlotPreferences.getDefault();
		plotPrefs.setTickLabelFontSize(18);
		plotPrefs.setAxisLabelFontSize(20);
		plotPrefs.setPlotLabelFontSize(21);
		plotPrefs.setBackgroundColor(Color.WHITE);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel(plotPrefs);
		
		String prefix = siteName.replaceAll(" ", "_")+"_curves_"+(float)period+"s_"+gmpeRef.getShortName();
		gp.drawGraphPanel(spec, true, true, xRange, yRange);
		gp.getChartPanel().setSize(800, 600);
		File pngFile = new File(outputDir, prefix+".png");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		System.out.println("DONE "+gmpeRef.getShortName()+", "+site.getName()+", "+xAxisLabel);
		return pngFile;
	}
	
	public void generateGMPE_Page(File outputDir, List<String> headerLines, AttenRelRef gmpeRef, double[] periods,
			List<? extends RuptureComparison<E>> comps, List<Site> highlightSites) throws IOException {
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		LinkedList<String> lines = new LinkedList<>();
		if (headerLines != null && !headerLines.isEmpty()) {
			lines.addAll(headerLines);
			if (!lines.getLast().isEmpty())
				lines.add("");
		}
		
		String distDescription = getDistDescription();
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		
		List<Site> sites = new ArrayList<>();
		if (highlightSites == null)
			// make plots for all sites
			sites.addAll(this.sites);
		else
			// make plots for only the specified, use all for aggregations
			sites.addAll(highlightSites);
		// add null for all sites aggregated
		sites.add(0, null);
		
		for (Site site : sites) {
			List<? extends RuptureComparison<E>> siteComps;
			List<Site> scatterSites;
			String siteName;
			if (site == null) {
				System.out.println("All sites aggregated");
				lines.add("## All Sites Aggregated");
				lines.add(topLink); lines.add("");
				lines.add("**"+this.sites.size()+" sites**");
				lines.add("");
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine("Name", "Location", "# Ruptures", "Vs30 (m/s)", "Z1.0 (km)", "Z2.5 (km)");
				for (Site s : this.sites) {
					table.initNewLine();
					table.addColumn(s.getName());
					Location loc = s.getLocation();
					table.addColumn("*"+(float)loc.getLatitude()+", "+(float)loc.getLongitude()+"*");
					table.addColumn(simProv.getRupturesForSite(s).size()+" ("+simProv.getNumSimlationsForSite(s)+" sims)");
					table.addColumn(optionalDigitDF.format(s.getParameter(Double.class, Vs30_Param.NAME).getValue()));
					Double z1 = s.getParameter(Double.class, DepthTo1pt0kmPerSecParam.NAME).getValue();
					if (z1 == null || Double.isNaN(z1))
						table.addColumn("N/A");
					else
						table.addColumn(optionalDigitDF.format(z1/1000d));
					Double z25 = s.getParameter(Double.class, DepthTo2pt5kmPerSecParam.NAME).getValue();
					if (z25 == null || Double.isNaN(z25))
						table.addColumn("N/A");
					else
						table.addColumn(optionalDigitDF.format(z25));
					table.finalizeLine();
				}
				lines.addAll(table.build());
				siteComps = comps;
				siteName = "All Sites";
				scatterSites = this.sites;
				lines.add("");
				lines.add(siteComps.size()+" ruptures within "+optionalDigitDF.format(cutoffDist)+" km of *any* site");
			} else {
				System.out.println("Site: "+site.getName());
				siteName = site.getName();
				lines.add("## Site "+site.getName());
				lines.add(topLink); lines.add("");
				Location loc = site.getLocation();
				lines.add("*Location: "+(float)loc.getLatitude()+", "+(float)loc.getLongitude()+"*");
				siteComps = new RuptureComparisonFilter.SiteFilter<E>().getMatches(comps, site);
				scatterSites = new ArrayList<>();
				scatterSites.add(site);
				
				lines.add(siteComps.size()+" ruptures within "+(float)cutoffDist+" km");
			}
			
			for (int m=0; m<magFilters.size(); m++) {
				RuptureComparisonFilter<E> magFilter = magFilters.get(m);
				lines.add("### "+siteName+", "+magLabels.get(m));
				
				List<? extends RuptureComparison<E>> magEventComps = magFilter.getMatches(comps, null);
				lines.add(magEventComps.size()+" Ruptures");
				
				lines.add("#### "+siteName+", "+magLabels.get(m)+", Scatter Plots");
				lines.add(topLink); lines.add("");
				lines.add("**Legend**");
				lines.add("* Red +: GMPE Mean/"+simName+" single rupture comparison");
				lines.add("* Yellow Region: Factor of 2 above & below");
				lines.add("* Green Line: Linear Regression");
				
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.initNewLine().addColumn("**Distance Bin**");
				for (double period : periods)
					table.addColumn("**"+optionalDigitDF.format(period)+" s**");
				table.finalizeLine();
				
				for (int d=0; d<distFilters.size(); d++) {
					RuptureComparisonFilter<E> filter = distFilters.get(d);
//					if (eventsForMagDist.isEmpty()) {
//						System.out.println("No events for "+site.getName()+", "+distLabels.get(d)+", "+magLabels.get(m));
//					}
					
					table.initNewLine().addColumn("**"+distLabels.get(d)+"**");
					
					for (double period : periods) {
						String prefix = siteName.replaceAll(" ", "_")+"_"+magFileLabels.get(m)+"_"+distFileLabels.get(d)
						+"_"+optionalDigitDF.format(period)+"s_"+gmpeRef.getShortName()+"_scatter";
				
						System.out.println("Plotting Scatter: "+prefix);
						
						List<String> binDescriptions = Lists.newArrayList(distLabels.get(d),
								magLabels.get(m), optionalDigitDF.format(period)+"s SA, "+gmpeRef.getShortName());
						boolean success = plotScatter(magEventComps, scatterSites, period, gmpeRef, filter,
								binDescriptions, resourcesDir, prefix);
						if (success) {
							File scatterPlot = new File(resourcesDir, prefix+".png");
							Preconditions.checkState(scatterPlot.exists());
							table.addColumn("![Scatter Plot]("+resourcesDir.getName()
								+"/"+scatterPlot.getName()+")");
						} else {
							table.addColumn("N/A");
						}
					}
					
					table.finalizeLine();
				}
				lines.add("");
				lines.addAll(table.wrap(max_table_fig_columns, 1).build());
				
				lines.add("#### "+siteName+", "+magLabels.get(m)+", Standard Normal Plots");
				lines.add(topLink); lines.add("");
				lines.add("These plots compare "+simName+" to the full GMPE log-normal distributions. "
						+ "Each rupture's GMPE distribution is converted to a standard log-normal "
						+ "distribution, and the z-score is computed for each rupture:");
				lines.add("");
				lines.add("**z-score**: (ln(*"+simName+"*) - ln(*GMPE-mean*)) / *GMPE-sigma*");
				lines.add("");
				lines.add("**Legend**");
				lines.add("* Black Line: Standard Normal distribution (in natural log space)");
				lines.add("* Gray Histogram: z-score for each rupture");
				lines.add("* Blue Dashed Line: "+simName+" Mean");
				
				table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				
				for (int d=0; d<distFilters.size(); d++)
					table.addColumn("**"+distLabels.get(d)+"**");
				table.finalizeLine();
				
				table.initNewLine();
				for (int d=0; d<distFilters.size(); d++) {
					RuptureComparisonFilter<E> filter = distFilters.get(d);
					String prefix = siteName.replaceAll(" ", "_")+"_"+magFileLabels.get(m)+"_"+distFileLabels.get(d)
						+"_"+gmpeRef.getShortName()+"_std_norm";
					
					System.out.println("Plotting Standard Normal: "+prefix);
					
					List<String> binDescriptions = Lists.newArrayList(distLabels.get(d), magLabels.get(m));
					boolean success = ZScoreHistPlot.plotStandardNormal(simProv, magEventComps, scatterSites, periods,
							gmpeRef, filter, binDescriptions, resourcesDir, prefix);
					if (success) {
						File plotFile = new File(resourcesDir, prefix+".png");
						Preconditions.checkState(plotFile.exists());
						table.addColumn("![Standard Normal Plot]("+resourcesDir.getName()
							+"/"+plotFile.getName()+")");
					} else {
						table.addColumn("N/A");
					}
				}
				table.finalizeLine();
				lines.add("");
				lines.addAll(table.wrap(max_table_fig_columns, 0).build());
			}
			String prefix = siteName.replaceAll(" ", "_")+"_all_mags_all_dists_"+gmpeRef.getShortName()+"_std_norm";
			boolean success = ZScoreHistPlot.plotStandardNormal(simProv, siteComps, scatterSites, periods,
					gmpeRef, null, new ArrayList<>(), resourcesDir, prefix);
			if (success) {
				lines.add("### "+siteName+", All Ruptures, Standard Normal Plots");
				lines.add(topLink); lines.add("");
				lines.add("");
				lines.add("z-score standard normal plots across all magnitudes/distances");
				lines.add("");
				lines.add("**z-score**: (ln(*"+simName+"*) - ln(*GMPE-mean*)) / *GMPE-sigma*");
				lines.add("");
				lines.add("**Legend**");
				lines.add("* Black Line: Standard Normal distribution (in natural log space)");
				lines.add("* Gray Histogram: z-score for each rupture");
				lines.add("* Blue Dashed Line: "+simName+" Mean");
				
				lines.add("");
				File plotFile = new File(resourcesDir, prefix+".png");
				Preconditions.checkState(plotFile.exists());
				lines.add("![Standard Normal Plot]("+resourcesDir.getName()
					+"/"+plotFile.getName()+")");
			}
		}
		
		// now hazard curves
		List<List<File>> curveFiles = new ArrayList<>();
		List<Site> curveSites;
		if (this.sites.size() > 15 && highlightSites != null)
			curveSites = highlightSites;
		else
			curveSites = this.sites;
		
		for (double period : periods)
			curveFiles.add(plotHazardCurves(comps, curveSites, period, gmpeRef, resourcesDir));
		
		lines.add("## Hazard Curves");
		lines.add(topLink); lines.add("");
		lines.add("**Legend**:");
		lines.add("* Black Solid Line: "+simName);
		for (int i=0; i<gmpe_truncs.length; i++) {
			String truncAdd = "";
			if (gmpe_truncs[i] > 0)
				truncAdd = " "+optionalDigitDF.format(gmpe_truncs[i])+"-sigma truncation";
			PlotLineType type = gmpe_trunc_line_types[i % gmpe_trunc_line_types.length];
			String lineType = type.name().replaceAll("_", " ");
			lineType = lineType.substring(0, 1).toUpperCase()+lineType.substring(1).toLowerCase();
			lines.add("* Blue "+lineType+" Line: "+gmpeRef.getShortName()+truncAdd);
		}
		lines.add("* Green Dashed Line: "+gmpeRef.getShortName()+" mean values only");
		lines.add("* Gray Dashed Lines: "+Joiner.on(" yr, ").join(Ints.asList(hazard_curve_rps))+" yr return periods");
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine().addColumn("Site");
		for (double period : periods)
			table.addColumn(optionalDigitDF.format(period)+"s");
		table.finalizeLine();
		for (int s=0; s<curveSites.size(); s++) {
			Site site = curveSites.get(s);
			table.initNewLine().addColumn("**"+site.getName()+"**");
			for (int p=0; p<periods.length; p++) {
				File plotFile = curveFiles.get(p).get(s);
				table.addColumn("![Hazard Curve]("+resourcesDir.getName()
							+"/"+plotFile.getName()+")");
			}
			table.finalizeLine();
		}
		lines.addAll(table.build());
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add(tocIndex, "## Table Of Contents");

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	protected abstract double calcRupAzimuthDiff(E event, int simIndex, Site site);
	
	protected static double calcRupAzimuthDiff(Location rupStart, Location rupEnd, Location centroid, Location hypo,
			Location siteLoc) {
		double hypoDist1 = LocationUtils.horzDistanceFast(hypo, rupStart);
		double hypoDist2 = LocationUtils.horzDistanceFast(hypo, rupEnd);
		if (hypoDist2 < hypoDist1) {
			// flip so that hypocenter is closest to loc1
			Location t = rupStart;
			rupStart = rupEnd;
			rupEnd = t;
		}
		
		double refAz = LocationUtils.azimuth(rupStart, rupEnd);
		double locAz = LocationUtils.azimuth(centroid, siteLoc);
		double diff1 = Math.abs(locAz - refAz);
		double diff2 = Math.abs((360+locAz) - refAz);
		double diff3 = Math.abs(locAz - (360+refAz));
		double diff = Math.min(diff1, Math.min(diff2, diff3));
		Preconditions.checkState(diff <= 180);
		return diff;
	}
	
	private synchronized double getRupAzimuthDiff(E event, int simIndex, Site site) {
		Map<Integer, Double> azMap = rupSiteAzMap.get(event, site);
		if (azMap == null) {
			azMap = new HashMap<>();
			rupSiteAzMap.put(event, site, azMap);
		}
		Double az = azMap.get(simIndex);
		if (az == null) {
			az = calcRupAzimuthDiff(event, simIndex, site);
			azMap.put(simIndex, az);
		}
		return az;
	}
	
	public boolean plotAggregateRotDRatio(Range magRange, File outputDir, String prefix) throws IOException {
		List<DiscretizedFunc[]> ratios = new ArrayList<>();
		
		for (Site site : sites) {
			for (E rupture : simProv.getRupturesForSite(site)) {
				if (magRange != null && !magRange.contains(simProv.getMagnitude(rupture)))
					continue;
				
				for (DiscretizedFunc ratio : simProv.getRotDRatios(site, rupture)) {
					ratios.add(new DiscretizedFunc[] {ratio});
				}
			}
		}
		if (ratios.isEmpty())
			return false;
		
		String title = "RotD100/50 Ratio";
		if (magRange == null)
			title += ", All Mags";
		else
			title += ", "+optionalDigitDF.format(magRange.getLowerBound())
				+"≤M≤"+optionalDigitDF.format(magRange.getUpperBound());
		
		SpectraPlotter.plotRotDRatio(ratios, simName, title, outputDir, prefix);
		
		return true;
	}
	
	private enum RatioDependence {
		DIST("Distance Dependence", "Distance", false, false),
		AZIMUTH("Azimuth Dependence", "Hypocentral Azimuth", false, false),
		ROTD50("RotD50 Dependence", "RotD50", true, true),
		GMPE_Z("Z-Value Dependence", "GMPE Z", true, false);
		
		private String plotTitle;
		private String axisLabel;
		private boolean periodDependent;
		private boolean logX;

		private RatioDependence(String plotTitle, String axisLabel, boolean periodDependent, boolean logX) {
			this.plotTitle = plotTitle;
			this.axisLabel = axisLabel;
			this.periodDependent = periodDependent;
			this.logX = logX;
		}
		
		public boolean isPeriodDependent() {
			return periodDependent;
		}
		
		public String getPlotTitle() {
			return plotTitle;
		}
		
		public String getAxisLabel() {
			return axisLabel;
		}
		
		public boolean isLogX() {
			return logX;
		}
	}
	
	public boolean plotRotDRatioDependence(Range magRange, Range distRange, double[] periods, boolean scatter, int numPoints,
			File outputDir, String prefix, RatioDependence quantity, List<? extends RuptureComparison<E>> gmpeComps)
					throws IOException {
		
		List<DiscretizedFunc[]> rotDs = new ArrayList<>();
		List<List<Double>> scalars = new ArrayList<>();
		if (quantity.isPeriodDependent()) {
			// individual ones
			for (int i=0; i<periods.length; i++)
				scalars.add(new ArrayList<>());
		} else {
			// share it
			List<Double> vals = new ArrayList<>();
			for (int i=0; i<periods.length; i++)
				scalars.add(vals);
		}
		
		for (Site site : sites) {
			for (RuptureComparison<E> comp : new RuptureComparisonFilter.SiteFilter<E>().getMatches(gmpeComps, site)) {
				if (magRange != null && !magRange.contains(comp.getMagnitude()))
					continue;
				
				double dist = Double.NaN;
				if (distRange != null || quantity == RatioDependence.DIST) {
					if (distJB)
						dist = comp.getDistanceJB(site);
					else
						dist = comp.getDistanceRup(site);
					if (distRange != null && !distRange.contains(dist))
						continue;
				}
				
				for (int index=0; index<simProv.getNumSimulations(site, comp.getRupture()); index++) {
					DiscretizedFunc ratio = simProv.getRotDRatio(site, comp.getRupture(), index);
					DiscretizedFunc rd50Func = null;
					if (quantity == RatioDependence.ROTD50 || quantity == RatioDependence.GMPE_Z)
						rd50Func = simProv.getRotD50(site, comp.getRupture(), index);
					
					switch (quantity) {
					case DIST:
						scalars.get(0).add(dist);
						break;
					case AZIMUTH:
						scalars.get(0).add(getRupAzimuthDiff(comp.getRupture(), index, site));
						break;
					case ROTD50:
						for (int p=0; p<periods.length; p++)
							scalars.get(p).add(rd50Func.getInterpolatedY(periods[p]));
						break;
					case GMPE_Z:
						for (int p=0; p<periods.length; p++) {
							double simVal = Math.log(rd50Func.getInterpolatedY(periods[p]));
							double gmpeVal = comp.getLogMean(site, periods[p]);
							double gmpeSigma = comp.getStdDev(site, periods[p]);
							double z = (simVal - gmpeVal)/gmpeSigma;
							scalars.get(p).add(z);
						}
						break;

					default:
						throw new IllegalStateException("unknown quanitity: "+quantity);
					}
					
					rotDs.add(new DiscretizedFunc[] {ratio});
				}
			}
		}
		if (rotDs.isEmpty())
			return false;
		
		String title = "RotD100/50 "+quantity.getPlotTitle();
		
		String scalarLabel = quantity.getAxisLabel();
		if (quantity == RatioDependence.DIST)
			scalarLabel = getDistShortName()+" (km)";
		
		if (magRange == null)
			title += ", All Mags";
		else
			title += ", "+optionalDigitDF.format(magRange.getLowerBound())
				+"≤M≤"+optionalDigitDF.format(magRange.getUpperBound());
		
		if (scatter) {
			for (int i=0; i<periods.length; i++) {
				double period = periods[i];
				String myPrefix = prefix+"_"+optionalDigitDF.format(period)+"s";
				SpectraPlotter.plotRotDRatioScatter(rotDs, scalars.get(i), scalarLabel, period,
						simName, title, outputDir, myPrefix, quantity.isLogX(), numPoints);
			}
		} else {
			SpectraPlotter.plotRotDRatioPeriodDependence(rotDs, scalars, scalarLabel, numPoints, periods,
					simName, title, outputDir, prefix, quantity.isLogX());
		}
		
		return true;
	}
	
	public void generateRotDRatioPage(File outputDir, List<String> headerLines, double[] aggregatedPeriods, double[] scatterPeriods,
			AttenRelRef gmpeRef, List<? extends RuptureComparison<E>> gmpeComps) throws IOException {
		Preconditions.checkState(simProv.hasRotD100());
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		LinkedList<String> lines = new LinkedList<>();
		
		String distShortName = getDistShortName();
		String distDescription = getDistDescription();
		
		// header
		if (headerLines != null && !headerLines.isEmpty()) {
			lines.addAll(headerLines);
			if (!lines.getLast().isEmpty())
				lines.add("");
		}
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		
		lines.add("## Site List");
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Name", "Location", "# Events");
		for (Site s : this.sites) {
			table.initNewLine();
			table.addColumn(s.getName());
			Location loc = s.getLocation();
			table.addColumn("*"+(float)loc.getLatitude()+", "+(float)loc.getLongitude()+"*");
			table.addColumn(simProv.getRupturesForSite(s).size()+"");
			table.finalizeLine();
		}
		lines.addAll(table.build());
		
		Range totalDistRange = new Range(0d, cutoffDist);
		
		for (int m=-1; m<magRanges.size(); m++) {
			Range magRange;
			String magLabel;
			String magFileLabel;
			if (m < 0) {
				magRange = null;
				magFileLabel = "all_mags";
				magLabel = "All Mags";
			} else {
				magRange = magRanges.get(m);
				if (magRange.getUpperBound() > 8)
					// not useful here
					continue;
				magFileLabel = magFileLabels.get(m);
				magLabel = magLabels.get(m);
			}

			lines.add("## "+magLabel);
			lines.add(topLink); lines.add("");
			
			// first total
			System.out.println("Plotting "+magLabel+" total RotD ratio");
			String prefix = "rot_d_ratio_"+magFileLabel+"_aggregated";
			Preconditions.checkState(plotAggregateRotDRatio(magRange, resourcesDir, prefix));
			lines.add("![RotD Ratio]("+resourcesDir.getName()+"/"+prefix+".png)");
			lines.add("");
			
			lines.add("### "+magLabel+", "+distShortName+" Dependence");
			lines.add(topLink); lines.add("");
			System.out.println("Plotting "+magLabel+" RotD ratio distance dependence");
			prefix = "rot_d_ratio_"+magFileLabel+"_"+distShortName+"_dependence";
			Preconditions.checkState(plotRotDRatioDependence(
					magRange, totalDistRange, aggregatedPeriods, false, 20, resourcesDir, prefix, RatioDependence.DIST, gmpeComps));
			lines.add("![RotD Ratio]("+resourcesDir.getName()+"/"+prefix+".png)");
			lines.add("");
			
			lines.add("### "+magLabel+", Azimuth Dependence");
			lines.add(topLink); lines.add("");
			System.out.println("Plotting "+magLabel+" RotD ratio azimuth dependence");
			prefix = "rot_d_ratio_"+magFileLabel+"_az_dependence";
			Preconditions.checkState(plotRotDRatioDependence(
					magRange, totalDistRange, aggregatedPeriods, false, 40, resourcesDir, prefix, RatioDependence.AZIMUTH, gmpeComps));
			lines.add("![RotD Ratio]("+resourcesDir.getName()+"/"+prefix+".png)");
			lines.add("");
			
			lines.add("### "+magLabel+", RotD50 Dependence");
			lines.add(topLink); lines.add("");
			System.out.println("Plotting "+magLabel+" RotD ratio amplitude dependence");
			prefix = "rot_d_ratio_"+magFileLabel+"_amplitude_dependence";
			Preconditions.checkState(plotRotDRatioDependence(
					magRange, totalDistRange, aggregatedPeriods, false, 20, resourcesDir, prefix, RatioDependence.ROTD50, gmpeComps));
			lines.add("![RotD Ratio]("+resourcesDir.getName()+"/"+prefix+".png)");
			lines.add("");
			
			lines.add("### "+magLabel+", GMPE Z Dependence");
			lines.add(topLink); lines.add("");
			lines.add("**z-score**: (ln(*"+simName+"*) - ln(*GMPE-mean*)) / *GMPE-sigma*");
			lines.add("");
			lines.add("**GMPE**: "+gmpeRef.getName());
			lines.add("");
			System.out.println("Plotting "+magLabel+" RotD ratio GMPE Z dependence");
			prefix = "rot_d_ratio_"+magFileLabel+"_z_dependence";
			Preconditions.checkState(plotRotDRatioDependence(
					magRange, totalDistRange, aggregatedPeriods, false, 20, resourcesDir, prefix, RatioDependence.GMPE_Z, gmpeComps));
			lines.add("![RotD Ratio]("+resourcesDir.getName()+"/"+prefix+".png)");
			lines.add("");
			
			lines.add("#### "+magLabel+", GMPE Z Dependence Scatters");
			lines.add(topLink); lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("Distance");
			for (double period : scatterPeriods)
				table.addColumn("**"+optionalDigitDF.format(period)+"s**");
			table.finalizeLine();
			for (int d=0; d<distRanges.size(); d++) {
				Range distRange = distRanges.get(d);
				String distLabel = distLabels.get(d);
				String distFileLabel = distFileLabels.get(d);
				
				table.initNewLine().addColumn("**"+distLabel+"**");
				
				prefix = "rot_d_ratio_"+magFileLabel+"_"+distFileLabel+"_scatter";
				Preconditions.checkState(plotRotDRatioDependence(magRange, distRange, scatterPeriods, true, 20, resourcesDir, prefix,
						RatioDependence.GMPE_Z, gmpeComps));
				for (double period : scatterPeriods) {
					File plotFile = new File(resourcesDir, prefix+"_"+optionalDigitDF.format(period)+"s.png");
					Preconditions.checkState(plotFile.exists());
					table.addColumn("![RotD Ratio]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
				}
				
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
//			table.addLine("Azimuth", "RotD100/RotD50 Ratio");
//			for (int a=0; a<azRanges.size(); a++) {
//				Range azRange = azRanges.get(a);
//				prefix = "rot_d_ratio_"+magFileLabel+"_"+azFileLabels.get(a);
//				table.initNewLine();
//				if (azRange == null)
//					table.addColumn("All");
//				else
//					table.addColumn(optionalDigitDF.format(azRange.getLowerBound())
//							+"°≤Az≤"+optionalDigitDF.format(azRange.getUpperBound())+"°");
//				if (plotRotDRatio(magRange, azRange, resourcesDir, prefix))
//					table.addColumn("![RotD Ratio]("+resourcesDir.getName()+"/"+prefix+".png)");
//				else
//					table.addColumn("N/A");
//				table.finalizeLine();
//			}
//			lines.addAll(table.build());
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add(tocIndex, "## Table Of Contents");

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	public void shutdown() {
		exec.shutdown();
	}

}