#!/usr/bin/python

# usage: computeAUC.py <predictions> <truth>

import sys
import re
import Queue

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


pred = readFile(sys.argv[1])
truth = readFile(sys.argv[2])


queue = Queue.PriorityQueue()

for author1 in pred:
	for author2 in pred[author1]:
		queue.put((-pred[author1][author2], (author1, author2)))


fp = 0
tp = 0
fn = 0
for author1 in truth:
	fn += len(truth[author1])


prevP = 0
prevR = float(tp) / float(tp + fn)

area = 0

while not queue.empty():

	temp = queue.get()

	pair = temp[1]

	if pair[0] in truth and pair[1] in truth[pair[0]]:
		tp += 1
		fn -= 1
	else:
		fp += 1

	precision = float(tp) / float(tp + fp)
	recall = float(tp) / float(tp + fn)

	base = recall - prevR
	height = (precision + prevP) / 2

	area += base * height

	prevR = recall
	prevP = precision

print "AUC: %f" % (area)