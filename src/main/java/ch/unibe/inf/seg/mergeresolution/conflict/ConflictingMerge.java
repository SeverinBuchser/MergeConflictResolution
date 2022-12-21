package ch.unibe.inf.seg.mergeresolution.conflict;

import ch.unibe.inf.seg.mergeresolution.resolution.ResolutionFile;
import ch.unibe.inf.seg.mergeresolution.resolution.ResolutionMerge;
import ch.unibe.inf.seg.mergeresolution.util.path.ConnectableIntersection;
import ch.unibe.inf.seg.mergeresolution.util.path.Intersections;
import ch.unibe.inf.seg.mergeresolution.util.path.IntersectionsIterator;
import ch.unibe.inf.seg.mergeresolution.util.path.SizeableIterable;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * Class representing a conflicting merge.
 * The conflicting files will automatically be searched and will be available by iterating.
 */
public class ConflictingMerge implements SizeableIterable<ResolutionMerge> {
    /**
     * The repository to which this conflicting merge belongs to.
     */
    private final Repository repository;
    /**
     * The commit which this conflicting merge represents.
     */
    protected final RevCommit commit;
    /**
     * The merge results of this conflicting merge.
     */
    private final Map<String, MergeResult<? extends Sequence>> mergeResults;
    /**
     * The conflicting files of this conflicting merge.
     */
    private final ArrayList<ConflictingFile> conflictingFiles;

    /**
     * Getter for {@link #conflictingFiles}
     * @return The conflicting files of this conflicting merge.
     */
    public ArrayList<ConflictingFile> getConflictingFiles() {
        return this.conflictingFiles;
    }

    /**
     * Initiates a new conflicting merge.
     * @param repository The repository to which this conflicting merge belongs to.
     * @param commit The commit which this conflicting merge represents.
     * @param mergeResults The merge results of this conflicting merge.
     */
    public ConflictingMerge(
            Repository repository,
            RevCommit commit,
            Map<String, MergeResult<? extends  Sequence>> mergeResults
    ) {
        this.repository = repository;
        this.commit = commit;
        this.mergeResults = mergeResults;
        this.conflictingFiles = this.findConflictingFiles();
    }

    /**
     * Finds and returns each conflicting file of the conflicting merge.
     * The merge obtains a {@link Map} of merge results and file names. The merge results belong to one specific merge
     * result. If the merge results contain conflicts the method adds a conflicting file to a list.
     * @return A list containing all conflicting files of this conflicting merge.
     */
    private ArrayList<ConflictingFile> findConflictingFiles() {
        ArrayList<ConflictingFile> conflictingFiles = new ArrayList<>();

        for (String fileName: this.mergeResults.keySet()) {
            if (!this.mergeResults.get(fileName).containsConflicts()) continue;

            ConflictingFile conflictingFile = new ConflictingFile(
                    this.repository,
                    this.commit,
                    this.mergeResults.get(fileName),
                    fileName
            );

            conflictingFiles.add(conflictingFile);
        }
        return conflictingFiles;
    }

    /**
     * Creates in intersections object containing all resolution files of this conflicting merge.
     * For each conflicting file, which is an iterable of file resolutions a connectable intersection is created and
     * connected to the intersections. This allows the intersections iterable to iterate over every possible combination
     * of resolution files.
     * @return Intersections object of resolution files.
     */
    private Intersections<ResolutionFile> buildResolutions() {
        Intersections<ResolutionFile> intersections = new Intersections<>();
        for (ConflictingFile conflictingFile: this.conflictingFiles) {
            intersections.connect(new ConnectableIntersection<>(conflictingFile));
        }
        return intersections;
    }

    /**
     * Builds the actual merge resolution for this conflicting merge.
     * For each conflicting file the actual resolution file will be retrieved and added to the resolution merge.
     * @return The actual merge resolution for this conflicting merge.
     * @throws IOException If one of the conflicting files is not found.
     */
    public ResolutionMerge getActualResolution() throws IOException {
        ResolutionMerge resolutionMerge = new ResolutionMerge();
        for (ConflictingFile conflictingFile: this.conflictingFiles) {
            resolutionMerge.add(conflictingFile.getActualResolutionFile());
        }
        return resolutionMerge;
    }

    /**
     * Gets the commit id for this conflicting merge.
     * @return The commit id of this conflicting merge.
     */
    public String getCommitId() {
        return this.commit.getName();
    }

    /**
     * Gets a short version of the commit id of this conflicting merge.
     * @return The short version of the commit id of this conflicting merge.
     */
    public String getCommitIdShort() {
        return this.getCommitId().substring(0, 7);
    }

    /**
     * An iterator over every possible merge resolution.
     * The iterator wraps the intersections iterator from {@link #buildResolutions()} by generating the path of
     * resolution files and then generating the resolution merge for this path.
     * @return An iterator over every merge resolution for this conflicting merge.
     */
    @Override
    public Iterator<ResolutionMerge> iterator() {
        return new Iterator<>() {
            private final IntersectionsIterator<ResolutionFile> iterator = buildResolutions().iterator();
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ResolutionMerge next() {
                if (this.hasNext()) return new ResolutionMerge(iterator.next());
                else return null;
            }
        };
    }

    /**
     * The size for this conflicting merge.
     * @return The size of this conflicting merge, which is 0 if there are no conflicts or the multiplication of the
     * sizes of the conflicting files.
     */
    @Override
    public double size() {
        if (this.conflictingFiles.size() == 0) return 0;
        double size = 1;

        for (ConflictingFile conflictingFile : this.conflictingFiles) {
            size *= conflictingFile.size();
        }
        return size;
    }

    /**
     * The number of conflicts for this conflicting merge.
     * @return The number of conflicts for this conflicting merge.
     */
    public double getConflictCount() {
        return this.conflictingFiles.stream().reduce((double) 0, (conflictCount, conflictingFile) -> {
            return conflictCount + conflictingFile.getConflictCount();
        }, Double::sum);
    }
}
