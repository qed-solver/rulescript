package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushFoldableIntoBranches(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushFoldableIntoBranches")

	input, cols := e.scan("t", 2)
	cond := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	branch := e.f.ConstructCase(memo.TrueSingleton, memo.ScalarListExpr{
		e.f.ConstructWhen(cond, e.v(cols[0])),
	}, e.v(cols[1]))
	out := e.f.Metadata().AddColumn("out", types.Int)
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(branch), out),
	}, opt.ColSet{})

	want := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructCase(memo.TrueSingleton, memo.ScalarListExpr{
			e.f.ConstructWhen(cond, e.f.ConstructUnaryMinus(e.v(cols[0]))),
		}, e.f.ConstructUnaryMinus(e.v(cols[1]))), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	badInput, badCols := e.scan("t", 2)
	badCond := e.f.ConstructGt(e.v(badCols[0]), e.num(0))
	badBranch := e.f.ConstructCase(memo.TrueSingleton, memo.ScalarListExpr{
		e.f.ConstructWhen(badCond, e.v(badCols[0])),
	}, e.v(badCols[1]))
	badOut := e.f.Metadata().AddColumn("out", types.Int)
	nearMiss := e.f.ConstructProject(badInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(badBranch), badOut),
	}, opt.MakeColSet(badCols[0]))
	e.expectNoMatch(rule, nearMiss)
}
