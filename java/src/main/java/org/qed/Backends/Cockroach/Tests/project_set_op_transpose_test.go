package rulescript_test

import (
	"regexp"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectSetOpTranspose(t *testing.T) {
	rule := loadRule(t, "ProjectSetOpTranspose")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	p := e.f.Metadata().AddColumn("p", types.Int)
	n1 := e.f.Metadata().AddColumn("n1", types.Int)
	n2 := e.f.Metadata().AddColumn("n2", types.Int)
	priv := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   opt.ColList{out},
	}
	projItems := func(c opt.ColumnID, col opt.ColumnID) memo.ProjectionsExpr {
		return memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(c), e.num(1)), col)}
	}

	before := e.f.ConstructProject(e.f.ConstructUnionAll(l, r, priv), projItems(out, p), opt.ColSet{})
	want := e.f.ConstructProject(
		e.f.ConstructUnionAll(
			e.f.ConstructProject(l, projItems(lc[0], p), opt.ColSet{}),
			e.f.ConstructProject(
				e.f.ConstructProject(r, projItems(rc[0], p), opt.ColSet{}),
				memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(p), n1)},
				opt.ColSet{},
			),
			&memo.SetPrivate{
				LeftCols:  opt.ColList{p},
				RightCols: opt.ColList{n1},
				OutCols:   opt.ColList{n2},
			},
		),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(n2), p)},
		opt.ColSet{},
	)

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	itemID := regexp.MustCompile(`\(item [0-9]+ `)
	varID := regexp.MustCompile(`\(var [0-9]+\)`)
	normalize := func(x opt.Expr) string {
		return varID.ReplaceAllString(itemID.ReplaceAllString(e.format(x), "(item _ "), "(var _)")
	}
	if g, w := normalize(got), normalize(want); g != w {
		t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), e.format(got), e.format(want))
	}

	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructUnion(l, r, priv), projItems(out, p), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructIntersect(l, r, priv), projItems(out, p), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(l, projItems(lc[0], p), opt.ColSet{}))
	e.expectNoMatch(rule, want)
}
