package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectMerge(t *testing.T) {
	rule := loadRule(t, "ProjectMerge")
	e := newEnv(t)
	s, c := e.scan("t", 3)

	bottom := e.f.Metadata().AddColumn("bottom", types.Int)
	top := e.f.Metadata().AddColumn("top", types.Int)
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(c[0]), bottom),
	}, opt.ColSet{})
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(bottom)), top),
	}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(c[0])), top),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(c[0])), top),
	}, opt.ColSet{}))

	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(
		e.f.ConstructGt(e.v(bottom), e.num(0)),
	)))

	selectInner := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c[0]), e.num(0))))
	e.expectNoMatch(rule, e.f.ConstructProject(selectInner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(c[0])), top),
	}, opt.ColSet{}))
}
