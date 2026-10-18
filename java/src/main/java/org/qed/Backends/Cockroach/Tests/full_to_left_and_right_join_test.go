package rulescript_test

import (
	"fmt"
	"regexp"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

var computedColumnRef = regexp.MustCompile(`\((item|var) (\d+)`)

func TestFullToLeftAndRightJoin(t *testing.T) {
	rule := loadRule(t, "FullToLeftAndRightJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	notMatched := e.f.ConstructIsNot(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])), memo.TrueSingleton)
	rightA := e.f.Metadata().AddColumn("right_a", types.Int)
	rightB := e.f.Metadata().AddColumn("right_b", types.Int)
	unionA := e.f.Metadata().AddColumn("union_a", types.Int)
	unionB := e.f.Metadata().AddColumn("union_b", types.Int)

	before := e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{})
	rightBranch := e.f.ConstructProject(
		e.f.ConstructSelect(e.f.ConstructRightJoin(l, r, on, &memo.JoinPrivate{}), e.filters(notMatched)),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(lc[0]), rightA),
			e.f.ConstructProjectionsItem(e.v(rc[0]), rightB),
		},
		opt.ColSet{},
	)
	union := e.f.ConstructUnionAll(
		e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}),
		rightBranch,
		&memo.SetPrivate{
			LeftCols:  opt.ColList{lc[0], rc[0]},
			RightCols: opt.ColList{rightA, rightB},
			OutCols:   opt.ColList{unionA, unionB},
		},
	)
	want := e.f.ConstructProject(union, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(unionA), lc[0]),
		e.f.ConstructProjectionsItem(e.v(unionB), rc[0]),
	}, opt.ColSet{})

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	keep := map[string]bool{fmt.Sprintf("%d", lc[0]): true, fmt.Sprintf("%d", rc[0]): true}
	shape := func(plan string) string {
		return computedColumnRef.ReplaceAllStringFunc(plan, func(m string) string {
			sub := computedColumnRef.FindStringSubmatch(m)
			if keep[sub[2]] {
				return m
			}
			return "(" + sub[1] + " ?"
		})
	}
	if g, w := shape(e.format(got)), shape(e.format(want)); g != w {
		t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), e.format(got), e.format(want))
	}

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructFullJoin(l, r, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
}
