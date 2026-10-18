package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneSelectCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneSelectCols")
	scan, cols := e.scan("t", 3)
	filter := e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1)))
	selected := e.f.ConstructSelect(scan, filter)
	projected := e.f.ConstructProject(selected, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[1]), e.f.Metadata().AddColumn("projected", types.Int)),
	}, opt.ColSet{})

	pruned := e.f.ConstructProject(scan, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1]))
	wantSelect := e.f.ConstructSelect(pruned, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1))))
	want := e.f.ConstructProject(wantSelect, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[1]), projected.(*memo.ProjectExpr).Projections[0].Col),
	}, opt.ColSet{})
	e.expectRewrite(rule, projected, want)

	thirdCol := cols[2]
	nearMiss := e.f.ConstructProject(selected, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(thirdCol), e.f.Metadata().AddColumn("third", types.Int)),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
