#!/usr/local/bin/python

# quick script to read flat .dat file and output as predicates

import sys
import string
import re

for fold in range(0,2):

	fin = open('toyB.dat', 'r')

	authorName = open('psl/authorName.' + str(fold) +'.txt', 'w')
	paperTitle = open('psl/paperTitle.' + str(fold) + '.txt', 'w')
	paperVenue = open('psl/paperVenue.' + str(fold) + '.txt', 'w')
	venueName = open('psl/venueName.' + str(fold) + '.txt', 'w')
	authorOf = open('psl/authorOf.' + str(fold) + '.txt', 'w')
	samePaper = open('psl/samePaper.' + str(fold) + '.txt', 'w')
	sameAuthor = open('psl/sameAuthor.' + str(fold) + '.txt', 'w')
	sameVenue = open('psl/sameVenue.' + str(fold) + '.txt', 'w')

	# starting index for unique keys of papers (pid), authors (aid), and venues (vid)
	pid = 0
	aid = 100
	vid = 1000

	authorClusters = dict()
	venueClusters = dict()
	paperClusters = dict()

	# start parsing flat data
	for line in fin:
		tokens = line.strip().split("\t")
		authors = tokens[1].split(",")
		title = tokens[2]
		venue = tokens[3]
		authorIds = tokens[4].split(",")
		paperId = tokens[5]
		venueId = tokens[6]

		venueName.write(str(vid) + "\t" + venue + "\n")
		paperVenue.write(str(pid) + "\t" + str(vid) + "\n")
		paperTitle.write(str(pid) + "\t" + title + "\n")

		# tokenize author field in case multiple authors are listed
		for author,aCid in zip(authors,authorIds):
			authorOf.write(str(aid) + "\t" + str(pid) + "\n")
			authorName.write(str(aid) + "\t" + author + "\n")

			# populate true author entity map
			if aCid not in authorClusters:
				authorClusters[aCid] = set()
			authorClusters[aCid].add(aid)

			aid += 1

		# populate true venue entity map
		if venueId not in venueClusters:
			venueClusters[venueId] = set()
		venueClusters[venueId].add(vid)
		vid += 1

		# populate true paper entity map
		if paperId not in paperClusters:
			paperClusters[paperId] = set()
		paperClusters[paperId].add(pid)
		pid += 1


	# output sameAuthor pairs for all pairs in each cluster
	for author in authorClusters:
		for a1 in authorClusters[author]:
			for a2 in authorClusters[author]:
				sameAuthor.write(str(a1) + "\t" + str(a2) + "\t1.0\n")

	
	for paper in paperClusters:
		for p1 in paperClusters[paper]:
			for p2 in paperClusters[paper]:
				samePaper.write(str(p1) + "\t" + str(p2) + "\t1.0\n")


	for venue in venueClusters:
		for v1 in venueClusters[venue]:
			for v2 in venueClusters[venue]:
				sameVenue.write(str(v1) + "\t" + str(v2) + "\t1.0\n")

	authorName.close()
	paperTitle.close()
	paperVenue.close()
	venueName.close()
	authorOf.close()
	samePaper.close()
	sameAuthor.close()
	sameVenue.close()
	fin.close()

