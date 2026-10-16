package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushFoldableIntoBranches(t *testing.T) {
	rule := loadRule(t, "PushFoldableIntoBranches")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	u := e.f.Metadata().AddColumn("u", types.Int)
	cond := e.f.ConstructGt(e.v(sc[0]), e.v(sc[1]))
	branch := func(a, b opt.ScalarExpr) opt.ScalarExpr {
		return e.f.ConstructCase(memo.TrueSingleton, memo.ScalarListExpr{e.f.ConstructWhen(cond, a)}, b)
	}
	apply := func(x opt.ScalarExpr) opt.ScalarExpr { return e.f.ConstructPlus(x, e.num(1)) }
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(apply(branch(e.v(sc[0]), e.v(sc[1]))), u),
	}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(branch(apply(e.v(sc[0])), apply(e.v(sc[1]))), u),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(apply(e.v(sc[0])), u),
	}, opt.ColSet{}))
}
