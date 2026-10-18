package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneFileSourcePartitions(t *testing.T) {
	rule := loadRule(t, "PruneFileSourcePartitions")
	e := newEnv(t)
	source, cols := e.scan("nn3", 3)
	part := e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))
	data := e.f.ConstructEq(e.v(cols[2]), e.num(7))
	full := e.f.ConstructAnd(part, data)
	filtered := e.f.ConstructSelect(source, e.filters(full))
	out := e.f.Metadata().AddColumn("projected", e.v(cols[0]).DataType())
	projection := e.f.ConstructCoalesce(memo.ScalarListExpr{e.v(cols[0]), e.v(cols[1]), e.v(cols[2])})
	before := e.f.ConstructProject(filtered, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(projection, out)}, opt.ColSet{})
	pruned := e.f.ConstructSelect(source, e.filters(part))
	prunedFiltered := e.f.ConstructSelect(pruned, e.filters(part, data))
	wantOut := e.f.Metadata().AddColumn("projected", e.v(cols[0]).DataType())
	wantProjection := e.f.ConstructCoalesce(memo.ScalarListExpr{e.v(cols[0]), e.v(cols[1]), e.v(cols[2])})
	want := e.f.ConstructProject(prunedFiltered, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(wantProjection, wantOut)}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)
	near, nearCols := e.scan("u", 2)
	nearPart := e.f.ConstructEq(e.v(nearCols[0]), e.v(nearCols[1]))
	nearData := e.f.ConstructEq(e.v(nearCols[1]), e.num(7))
	nearFilter := e.f.ConstructSelect(near, e.filters(e.f.ConstructAnd(nearPart, nearData)))
	nearOut := e.f.Metadata().AddColumn("identity", e.v(nearCols[0]).DataType())
	nearMiss := e.f.ConstructProject(nearFilter, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearOut)}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
