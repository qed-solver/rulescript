package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWithScanCols(t *testing.T) {
	rule := loadRule(t, "PruneWithScanCols")
	e := newEnv(t)
	definition, inCols := e.scan("t", 3)
	outCols := opt.ColList{
		e.f.Metadata().AddColumn("w0", types.Int),
		e.f.Metadata().AddColumn("w1", types.Int),
		e.f.Metadata().AddColumn("w2", types.Int),
	}
	withID := e.f.Memo().NextWithID()
	e.f.Metadata().AddWithBinding(withID, definition)
	scan := e.f.ConstructWithScan(&memo.WithScanPrivate{
		With:    withID,
		Name:    "cte",
		InCols:  opt.ColList{inCols[0], inCols[1], inCols[2]},
		OutCols: outCols,
		ID:      e.f.Metadata().NextUniqueID(),
	})
	result := e.f.Metadata().AddColumn("result", types.Int)
	before := e.f.ConstructProject(scan,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[1])), result)},
		opt.ColSet{},
	)
	pruned := e.f.ConstructWithScan(&memo.WithScanPrivate{
		With:    scan.(*memo.WithScanExpr).With,
		Name:    "cte",
		InCols:  opt.ColList{inCols[0], inCols[1]},
		OutCols: opt.ColList{outCols[0], outCols[1]},
		ID:      scan.(*memo.WithScanExpr).ID,
	})
	wantInput := e.f.ConstructProject(pruned, memo.EmptyProjectionsExpr, opt.MakeColSet(outCols[0], outCols[1]))
	want := e.f.ConstructProject(wantInput,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[1])), result)},
		opt.ColSet{},
	)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearWithID := e.f.Memo().NextWithID()
	e.f.Metadata().AddWithBinding(nearWithID, definition)
	nearScan := e.f.ConstructWithScan(&memo.WithScanPrivate{
		With:    nearWithID,
		Name:    "cte",
		InCols:  opt.ColList{inCols[0], inCols[1], inCols[2]},
		OutCols: opt.ColList{outCols[0], outCols[1], outCols[2]},
		ID:      e.f.Metadata().NextUniqueID(),
	})
	nearResult := e.f.Metadata().AddColumn("near_result", types.Int)
	nearMiss := e.f.ConstructProject(nearScan,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[2])), nearResult)},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, nearMiss)
}
