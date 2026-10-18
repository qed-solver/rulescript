package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateDistinctSetRight(t *testing.T) {
	rule := loadRule(t, "EliminateDistinctSetRight")
	e := newEnv(t)
	rel, rc := e.scan("t", 3)
	proj := e.f.ConstructProject(rel, nil, opt.MakeColSet(rc...))
	var emptyCols, outCols opt.ColList
	for i := 0; i < 3; i++ {
		emptyCols = append(emptyCols, e.f.Metadata().AddColumn("empty", types.Int))
		outCols = append(outCols, e.f.Metadata().AddColumn("union", types.Int))
	}
	order := opt.ColList{rc[2], rc[0], rc[1]}
	private := &memo.SetPrivate{LeftCols: order, RightCols: emptyCols, OutCols: outCols}

	before := e.f.ConstructUnion(proj, e.empty(emptyCols...), private)
	grouped := e.f.ConstructGroupBy(proj, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc...)})
	want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(rc[2]), outCols[0]),
		e.f.ConstructProjectionsItem(e.v(rc[0]), outCols[1]),
		e.f.ConstructProjectionsItem(e.v(rc[1]), outCols[2]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructUnionAll(proj, e.empty(emptyCols...), private))
	e.expectNoMatch(rule, e.f.ConstructUnion(proj, e.intValues(emptyCols, []int{1, 2, 3}), private))
}
