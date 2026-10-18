package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestValuesReduce(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ValuesReduce")
	ca := e.f.Metadata().AddColumn("a", types.Int)
	cb := e.f.Metadata().AddColumn("b", types.Int)
	cc := e.f.Metadata().AddColumn("c", types.Int)
	input := e.intValues(opt.ColList{ca, cb, cc}, []int{1, 2, 3}, []int{4, 5, 6})
	before := e.f.ConstructProject(input, memo.EmptyProjectionsExpr, opt.MakeColSet(ca, cc))
	wantCols := opt.ColList{cc, ca}
	want := e.intValues(wantCols, []int{3, 1}, []int{6, 4})
	e.expectRewrite(rule, before, want)

	computed := e.f.Metadata().AddColumn("computed", types.Int)
	nearMiss := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(ca), e.num(1)), computed),
	}, opt.MakeColSet(cb, cc))
	e.expectNoMatch(rule, nearMiss)
}
