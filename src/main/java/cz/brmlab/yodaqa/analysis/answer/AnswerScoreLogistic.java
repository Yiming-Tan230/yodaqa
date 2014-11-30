package cz.brmlab.yodaqa.analysis.answer;

import java.util.LinkedList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.model.AnswerHitlist.Answer;

/**
 * Annotate the AnswerHitlistCAS Answer FSes with score based on the
 * present AnswerFeatures.  This particular implementation uses
 * the estimated probability of the answer being correct as determined
 * by logistic regression based classifier trained on the training set
 * using the data/ml/train-answer.py script. */


public class AnswerScoreLogistic extends JCasAnnotator_ImplBase {
	final Logger logger = LoggerFactory.getLogger(AnswerScoreLogistic.class);

	/** The weights of individual elements of the FV.  These weights
	 * are output by data/ml/answer-train.py as this:
	 *
	 * 430 answersets, 91421 answers
	 * + Cross-validation:
	 * (test) PERANS acc/prec/rcl/F2 = 0.757/0.058/0.602/0.208, @70 prec/rcl/F2 = 0.096/0.378/0.238, PERQ avail 0.693, any good = [0.526], simple 0.499
	 * (test) PERANS acc/prec/rcl/F2 = 0.767/0.056/0.587/0.202, @70 prec/rcl/F2 = 0.088/0.360/0.223, PERQ avail 0.763, any good = [0.490], simple 0.489
	 * (test) PERANS acc/prec/rcl/F2 = 0.777/0.063/0.545/0.215, @70 prec/rcl/F2 = 0.108/0.348/0.241, PERQ avail 0.781, any good = [0.580], simple 0.539
	 * (test) PERANS acc/prec/rcl/F2 = 0.785/0.059/0.579/0.210, @70 prec/rcl/F2 = 0.106/0.343/0.237, PERQ avail 0.735, any good = [0.489], simple 0.501
	 * (test) PERANS acc/prec/rcl/F2 = 0.756/0.060/0.615/0.217, @70 prec/rcl/F2 = 0.094/0.349/0.226, PERQ avail 0.744, any good = [0.490], simple 0.473
	 * (test) PERANS acc/prec/rcl/F2 = 0.762/0.058/0.591/0.210, @70 prec/rcl/F2 = 0.095/0.347/0.226, PERQ avail 0.716, any good = [0.495], simple 0.470
	 * (test) PERANS acc/prec/rcl/F2 = 0.741/0.063/0.625/0.225, @70 prec/rcl/F2 = 0.107/0.336/0.236, PERQ avail 0.758, any good = [0.498], simple 0.532
	 * (test) PERANS acc/prec/rcl/F2 = 0.758/0.065/0.643/0.231, @70 prec/rcl/F2 = 0.115/0.379/0.260, PERQ avail 0.758, any good = [0.515], simple 0.566
	 * (test) PERANS acc/prec/rcl/F2 = 0.749/0.063/0.628/0.224, @70 prec/rcl/F2 = 0.104/0.383/0.249, PERQ avail 0.740, any good = [0.529], simple 0.512
	 * (test) PERANS acc/prec/rcl/F2 = 0.744/0.056/0.623/0.207, @70 prec/rcl/F2 = 0.093/0.379/0.235, PERQ avail 0.735, any good = [0.505], simple 0.473
	 * Cross-validation score mean 51.151% S.D. 2.669%
	 * + Full training set:
	 * (full) PERANS acc/prec/rcl/F2 = 0.769/1.000/0.237/0.280, @70 prec/rcl/F2 = 1.000/0.085/0.104, PERQ avail 0.730, any good = [0.544], simple 0.510
	 * Full model is LogisticRegression(C=1.0, class_weight=auto, dual=False, fit_intercept=True,
		  intercept_scaling=1, penalty=l2, random_state=None, tol=0.0001)
	 */
	public static double weights[] = {
		/*                  occurences @,%,! */ -0.011357, -0.030304,  0.000000, /*                  occurences d01: -0.041661 */
		/*              resultLogScore @,%,! */  0.562544,  0.061015,  0.000000, /*              resultLogScore d01:  0.623559 */
		/*             passageLogScore @,%,! */ -0.210630,  0.639105,  0.118793, /*             passageLogScore d01:  0.309682 */
		/*                   originPsg @,%,! */ -0.042814, -0.481298,  0.118793, /*                   originPsg d01: -0.642905 */
		/*              originPsgFirst @,%,! */  0.140563, -0.184963, -0.064583, /*              originPsgFirst d01:  0.020184 */
		/*                 originPsgNP @,%,! */  0.379484,  0.229605, -0.303505, /*                 originPsgNP d01:  0.912595 */
		/*                 originPsgNE @,%,! */ -0.200757,  0.125098,  0.276736, /*                 originPsgNE d01: -0.352394 */
		/*        originPsgNPByLATSubj @,%,! */  0.313318, -0.018841, -0.237339, /*        originPsgNPByLATSubj d01:  0.531816 */
		/*           originPsgSurprise @,%,! */  0.060902, -0.023427,  0.015077, /*           originPsgSurprise d01:  0.022399 */
		/*              originDocTitle @,%,! */  0.528694,  0.138894, -0.452715, /*              originDocTitle d01:  1.120302 */
		/*           originDBpRelation @,%,! */  0.025381,  0.029136,  0.050598, /*           originDBpRelation d01:  0.003920 */
		/*               originConcept @,%,! */  0.025396, -0.333135,  0.050583, /*               originConcept d01: -0.358322 */
		/*      originConceptBySubject @,%,! */  0.401262, -0.125903, -0.325283, /*      originConceptBySubject d01:  0.600642 */
		/*          originConceptByLAT @,%,! */  0.479543, -0.686098, -0.403564, /*          originConceptByLAT d01:  0.197009 */
		/*           originConceptByNE @,%,! */  0.388646, -0.393199, -0.312667, /*           originConceptByNE d01:  0.308114 */
		/*              originMultiple @,%,! */ -0.109636, -0.171496,  0.185615, /*              originMultiple d01: -0.466747 */
		/*                   spWordNet @,%,! */ -0.155514,  0.235638, -0.434674, /*                   spWordNet d01:  0.514798 */
		/*               LATQNoWordNet @,%,! */ -0.326172,  0.000000,  0.402151, /*               LATQNoWordNet d01: -0.728323 */
		/*               LATANoWordNet @,%,! */  0.277490, -0.140813, -0.201511, /*               LATANoWordNet d01:  0.338188 */
		/*              tyCorPassageSp @,%,! */  1.285413,  0.051420,  0.139370, /*              tyCorPassageSp d01:  1.197463 */
		/*            tyCorPassageDist @,%,! */  0.259919, -0.115758,  0.139370, /*            tyCorPassageDist d01:  0.004791 */
		/*          tyCorPassageInside @,%,! */ -0.044344,  0.115374,  0.120323, /*          tyCorPassageInside d01: -0.049293 */
		/*                 simpleScore @,%,! */  0.005078,  0.123039,  0.000000, /*                 simpleScore d01:  0.128117 */
		/*                       LATNE @,%,! */ -0.260506,  0.274045,  0.336485, /*                       LATNE d01: -0.322947 */
		/*                  LATDBpType @,%,! */  0.775702, -0.761327, -0.699723, /*                  LATDBpType d01:  0.714098 */
		/*                 LATQuantity @,%,! */ -0.189792, -0.086554,  0.265771, /*                 LATQuantity d01: -0.542117 */
		/*               LATQuantityCD @,%,! */  0.677148, -0.255621, -0.601169, /*               LATQuantityCD d01:  1.022696 */
		/*               LATWnInstance @,%,! */  0.414712, -0.135487, -0.338733, /*               LATWnInstance d01:  0.617958 */
		/*              LATDBpRelation @,%,! */  0.025381,  0.029136,  0.050598, /*              LATDBpRelation d01:  0.003920 */
		/*                 tyCorSpQHit @,%,! */  0.609977, -0.004716, -0.533998, /*                 tyCorSpQHit d01:  1.139258 */
		/*                 tyCorSpAHit @,%,! */ -0.043418, -0.400370,  0.119397, /*                 tyCorSpAHit d01: -0.563184 */
		/*                    tyCorANE @,%,! */  1.049535, -0.097592, -0.973556, /*                    tyCorANE d01:  1.925498 */
		/*                   tyCorADBp @,%,! */  0.854527, -0.167059, -0.778548, /*                   tyCorADBp d01:  1.466017 */
		/*              tyCorAQuantity @,%,! */ -0.041281,  0.045742,  0.117260, /*              tyCorAQuantity d01: -0.112799 */
		/*            tyCorAQuantityCD @,%,! */ -0.885588,  0.856233,  0.961568, /*            tyCorAQuantityCD d01: -0.990923 */
		/*            tyCorAWnInstance @,%,! */ -0.508068,  0.234336,  0.584047, /*            tyCorAWnInstance d01: -0.857778 */
		/*           tyCorADBpRelation @,%,! */ -0.251507,  0.187912,  0.327486, /*           tyCorADBpRelation d01: -0.391080 */
	};
	public static double intercept = 0.075979;

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	protected class AnswerScore {
		public Answer a;
		public double score;

		public AnswerScore(Answer a_, double score_) {
			a = a_;
			score = score_;
		}
	}

	public void process(JCas jcas) throws AnalysisEngineProcessException {
		AnswerStats astats = new AnswerStats(jcas);

		List<AnswerScore> answers = new LinkedList<AnswerScore>();

		for (Answer a : JCasUtil.select(jcas, Answer.class)) {
			AnswerFV fv = new AnswerFV(a, astats);

			double t = intercept;
			double fvec[] = fv.getFV();
			for (int i = 0; i < fvec.length; i++) {
				t += fvec[i] * weights[i];
			}

			double prob = 1.0 / (1.0 + Math.exp(-t));
			answers.add(new AnswerScore(a, prob));
		}

		/* Reindex the touched answer info(s). */
		for (AnswerScore as : answers) {
			as.a.removeFromIndexes();
			as.a.setConfidence(as.score);
			as.a.addToIndexes();
		}
	}
}
