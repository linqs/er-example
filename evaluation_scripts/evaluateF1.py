#!/usr/bin/python

# usage evaluateF1.py <predictions> <truth>

import sys
import re

def readFile(filename):
	# load in a same-as predicate file into 2d hash table
	fin = open(filename, 'r')
	output = dict()
	for line in fin:
		tokens = re.split('\s', line)
		if tokens[0] not in output:
			output[tokens[0]] = dict()
		output[tokens[0]][tokens[1]] = float(tokens[2])
	fin.close()
	return output

def stats(pred, truth, threshold):
	# compute precision, recall, and f1
	tp = 0
	allpos = 0
	alltrue = 0

	for a in truth:
		for b in truth[a]:
			if a in pred and b in pred[a] and pred[a][b] >= threshold:
				tp += 1
			alltrue += 1
	for a in pred:
		for b in pred[a]:
			if pred[a][b] >= threshold:
				allpos += 1

	precision = float(tp) / float(allpos)
	recall = float(tp) / float(alltrue)
	f1 = 0
	if precision + recall > 0:
		f1 = 2 * precision * recall / (precision + recall)

	return [precision, recall, f1]


prediction = readFile(sys.argv[1])
truth = readFile(sys.argv[2])

thresholds = [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]

predP = dict()
predR = dict()
predF1 = dict()

for t in thresholds:
	predStats = stats(prediction, truth, t)
	predP[t] = predStats[0]
	predR[t] = predStats[1]
	predF1[t] = predStats[2]


# print PR curve files
fout = open('output/%s_stats.txt' % sys.argv[1], 'w')
for t in thresholds:
	fout.write("%f\t%f\t%f\n" % (predR[t], predP[t], predF1[t]))
fout.close()


