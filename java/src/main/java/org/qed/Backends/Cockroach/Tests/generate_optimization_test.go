package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestGenerateOptimization(t *testing.T) {
	rule := loadRule(t, "GenerateOptimization")
	e := newEnv(t)
	base, bc := e.scan("u", 1)
	jt1 := e.f.Metadata().AddColumn("jt1", types.Int)
	jt2 := e.f.Metadata().AddColumn("jt2", types.Int)
	jt3 := e.f.Metadata().AddColumn("jt3", types.Int)

	inner := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(bc[0]), jt1),
		e.f.ConstructProjectionsItem(e.v(bc[0]), jt2),
	}, opt.ColSet{})
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{}, opt.MakeColSet(jt2))
	want := e.f.ConstructProject(e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(bc[0]), jt2),
	}, opt.ColSet{}), memo.ProjectionsExpr{}, opt.MakeColSet(jt2))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(inner, memo.ProjectionsExpr{}, opt.MakeColSet(jt1)))

	threeOutputs := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(bc[0]), jt1),
		e.f.ConstructProjectionsItem(e.v(bc[0]), jt2),
		e.f.ConstructProjectionsItem(e.v(bc[0]), jt3),
	}, opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructProject(threeOutputs, memo.ProjectionsExpr{}, opt.MakeColSet(jt2)))

	filtered := e.f.ConstructSelect(inner, e.filters(e.f.ConstructEq(e.v(jt1), e.num(1))))
	e.expectNoMatch(rule, e.f.ConstructProject(filtered, memo.ProjectionsExpr{}, opt.MakeColSet(jt2)))
}
